package srooms.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import srooms.core.BOARD_SIZE
import srooms.core.CharacterSheet
import srooms.core.GameLogic
import srooms.core.GmNote
import srooms.core.LogEntry
import srooms.core.MAX_CARD_LEVEL
import srooms.core.PlayerColor
import srooms.core.PlayerInfo
import srooms.core.PlayerMessage
import srooms.core.Pos
import srooms.core.RoomColor
import srooms.core.SessionSave
import srooms.core.SessionState
import srooms.persistence.SaveStore
import srooms.protocol.ClientMsg
import srooms.protocol.GameView
import srooms.protocol.RoomView
import srooms.protocol.ServerMsg
import srooms.protocol.protocolJson
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class GameServer(
    private val port: Int,
    private val saveStore: SaveStore = SaveStore(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: EmbeddedServer<*, *>? = null

    private class Client(
        val id: String,
        var info: PlayerInfo,
        val session: WebSocketSession,
        val sendLock: Mutex = Mutex(),
    ) {
        suspend fun send(msg: ServerMsg) {
            sendLock.withLock {
                session.send(Frame.Text(protocolJson.encodeToString(ServerMsg.serializer(), msg)))
            }
        }
    }

    private val stateLock = Mutex()
    private val clients = LinkedHashMap<String, Client>()
    private var hostId: String? = null
    private var gmId: String? = null
    private var session: SessionState? = null

    fun start() {
        try {
            engine = embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }
                .start(wait = false)
        } catch (e: Exception) {
            engine = null
            throw PortInUseException(port, e)
        }
    }

    fun stop() {
        engine?.stop(500, 1000)
        engine = null
    }

    val actualPort: Int get() = port

    class PortInUseException(val busyPort: Int, cause: Throwable) :
        Exception("Порт $busyPort занят", cause)

    private fun Application.module() {
        install(WebSockets)
        routing {
            get("/") { call.respondText("SRooms server") }
            webSocket("/ws") { handleConnection() }
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleConnection() {
        var client: Client? = null
        try {
            val first = incoming.receive()
            if (first !is Frame.Text) {
                close()
                return
            }
            val join = runCatching {
                protocolJson.decodeFromString(ClientMsg.serializer(), first.readText())
            }.getOrNull() as? ClientMsg.Join
            if (join == null || join.name.isBlank()) {
                send(Frame.Text(protocolJson.encodeToString(ServerMsg.serializer(), ServerMsg.Error("Ожидалось сообщение Join с именем"))))
                close()
                return
            }

            val id = UUID.randomUUID().toString().take(8)
            client = Client(id, PlayerInfo(id = id, name = join.name.trim().take(24)), this)

            val rejected = stateLock.withLock {
                if (session != null) {
                    true
                } else if (clients.values.any { it.info.name.equals(client.info.name, ignoreCase = true) }) {
                    // имя — ключ личных заметок и листа персонажа, дубликаты недопустимы
                    client.send(ServerMsg.Error("Имя «${client.info.name}» уже занято"))
                    close()
                    return@handleConnection
                } else {
                    // авто-выдача первого свободного цвета, чтобы не было повторов
                    val taken = clients.values.mapNotNull { it.info.color }.toSet()
                    val freeColor = PlayerColor.entries.firstOrNull { it !in taken }
                    client.info = client.info.copy(color = freeColor)
                    clients[id] = client
                    if (hostId == null) {
                        hostId = id
                        gmId = id
                    }
                    false
                }
            }
            if (rejected) {
                client.send(ServerMsg.Error("Сессия уже идёт — подключиться нельзя"))
                close()
                return
            }

            client.send(ServerMsg.Welcome(playerId = id, isHost = id == hostId))
            broadcastLobby()

            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val msg = runCatching {
                    protocolJson.decodeFromString(ClientMsg.serializer(), frame.readText())
                }.getOrNull() ?: continue
                handleMessage(client, msg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // соединение оборвалось
        } finally {
            if (client != null) onDisconnect(client)
        }
    }

    private suspend fun handleMessage(client: Client, msg: ClientMsg) {
        stateLock.withLock {
            when (msg) {
                is ClientMsg.Join -> Unit
                is ClientMsg.SetColor -> handleSetColor(client, msg)
                is ClientMsg.AssignGm -> handleAssignGm(client, msg)
                is ClientMsg.StartSession -> handleStartSession(client, msg)
                is ClientMsg.ReturnToLobby -> handleReturnToLobby(client)
                is ClientMsg.LogInput -> handleLogInput(client, msg)
                is ClientMsg.UpdatePersonalNote -> handlePersonalNote(client, msg)
                is ClientMsg.SendPlayerMessage -> handlePlayerMessage(client, msg)
                is ClientMsg.SaveSession -> handleSave(client, msg)
                is ClientMsg.UpdateSheet -> handleUpdateSheet(client, msg)
                is ClientMsg.UpsertCard -> handleUpsertCard(client, msg)
                is ClientMsg.DeleteCard -> handleDeleteCard(client, msg)
                is ClientMsg.MoveCard -> handleMoveCard(client, msg)
                is ClientMsg.GmAction -> handleGmAction(client, msg)
            }
        }
    }

    // ---------- Лобби ----------

    private suspend fun handleSetColor(client: Client, msg: ClientMsg.SetColor) {
        if (session != null) return
        val taken = clients.values.any { it.id != client.id && it.info.color == msg.color }
        if (taken) {
            client.send(ServerMsg.Error("Цвет ${msg.color.displayName} уже занят"))
            return
        }
        client.info = client.info.copy(color = msg.color)
        broadcastLobby()
    }

    private suspend fun handleAssignGm(client: Client, msg: ClientMsg.AssignGm) {
        if (client.id != hostId || session != null) return
        if (msg.playerId !in clients) return
        gmId = msg.playerId
        broadcastLobby()
    }

    private suspend fun handleStartSession(client: Client, msg: ClientMsg.StartSession) {
        if (client.id != hostId || session != null) return
        val gm = gmId?.let { clients[it] } ?: client
        gmId = gm.id

        val loaded = msg.saveName?.let { name ->
            runCatching { saveStore.load(name) }.getOrElse {
                client.send(ServerMsg.Error("Не удалось загрузить сейв «$name»: ${it.message}"))
                return
            }
        }

        val base = loaded?.state ?: SessionState()
        session = base
        appendSystem("Сессия начата${loaded?.let { " (сейв «${it.name}»)" } ?: ""}. ГМ: ${gm.info.name}.")
        broadcastGame()
    }

    private suspend fun handleReturnToLobby(client: Client) {
        if (client.id != hostId || session == null) return
        session = null
        for (c in clients.values) {
            runCatching { c.send(ServerMsg.SessionEnded) }
        }
        broadcastLobby()
    }

    // ---------- Игра ----------

    private suspend fun handleLogInput(client: Client, msg: ClientMsg.LogInput) {
        val s = session ?: return
        val text = msg.text.trim()
        if (text.isEmpty()) return
        val rollReq = GameLogic.parseRoll(text)
        session = GameLogic.appendLog(
            s, LogEntry.Chat(client.id, client.info.name, client.info.color, text.take(500))
        )
        if (rollReq != null) {
            val results = GameLogic.roll(rollReq)
            session = GameLogic.appendLog(
                session!!,
                LogEntry.Roll(client.id, client.info.name, client.info.color, rollReq.count, rollReq.sides, results)
            )
        }
        broadcastGame()
    }

    private suspend fun handlePersonalNote(client: Client, msg: ClientMsg.UpdatePersonalNote) {
        val s = session ?: return
        session = s.copy(personalNotes = s.personalNotes + (client.info.name to msg.text.take(20000)))
        // личная заметка влияет только на вид автора — не спамим остальным
        sendGameTo(client)
    }

    private suspend fun handlePlayerMessage(client: Client, msg: ClientMsg.SendPlayerMessage) {
        val s = session ?: return
        if (client.id != gmId) return
        val target = clients[msg.toPlayerId] ?: return
        val text = msg.text.trim()
        if (text.isEmpty()) return
        val message = PlayerMessage(
            id = UUID.randomUUID().toString().take(8),
            toPlayerId = target.id,
            toPlayerName = target.info.name,
            text = text.take(2000),
        )
        session = GameLogic.appendMessage(s, message)
        runCatching { target.send(ServerMsg.PlayerMessageEvent(message)) }
        broadcastGame()
    }

    private suspend fun handleSave(client: Client, msg: ClientMsg.SaveSession) {
        val s = session ?: return
        if (client.id != gmId) return
        runCatching {
            saveStore.save(SessionSave(name = msg.name.trim(), savedAt = System.currentTimeMillis(), state = s))
        }.onFailure {
            client.send(ServerMsg.Error("Не удалось сохранить: ${it.message}"))
            return
        }
        appendSystem("Сессия сохранена как «${msg.name.trim()}».")
        broadcastGame()
    }

    private suspend fun handleGmAction(client: Client, action: ClientMsg.GmAction) {
        val s = session ?: return
        if (client.id != gmId) return

        var newState = s
        var logText: String? = null
        var gmOnly = false

        fun roomName(index: Int): String {
            val r = newState.rooms.getOrNull(index) ?: return "?"
            val pos = Pos.of(index)
            return r.label.ifBlank { "(${pos.x + 1};${pos.y + 1})" }
        }

        when (action) {
            is ClientMsg.GmAction.MoveToken -> {
                newState = GameLogic.moveToken(newState, action.pos)
                logText = "Фишка перемещена в ${roomName(action.pos.index())} (${action.pos.x + 1};${action.pos.y + 1})"
            }
            is ClientMsg.GmAction.SwapRooms -> {
                newState = GameLogic.swapRooms(newState, action.indexA, action.indexB)
                logText = "Комнаты ${roomName(action.indexA)} и ${roomName(action.indexB)} поменяны местами"
            }
            is ClientMsg.GmAction.ShiftRow -> {
                newState = GameLogic.shiftRow(newState, action.row, action.dir)
                logText = "Ряд ${action.row + 1} сдвинут ${if (action.dir > 0) "вправо" else "влево"}"
            }
            is ClientMsg.GmAction.ShiftCol -> {
                newState = GameLogic.shiftCol(newState, action.col, action.dir)
                logText = "Столбец ${action.col + 1} сдвинут ${if (action.dir > 0) "вниз" else "вверх"}"
            }
            is ClientMsg.GmAction.SetRoomColor -> {
                newState = GameLogic.updateRoom(newState, action.index) { it.copy(color = action.color) }
                logText = "Комната ${roomName(action.index)}: цвет → ${action.color.displayName}"
            }
            is ClientMsg.GmAction.SetRoomLabel -> {
                newState = GameLogic.updateRoom(newState, action.index) { it.copy(label = action.label.take(24)) }
                // подписи меняются посимвольно/дебаунсом — склеиваем в одну запись
                val pos = Pos.of(action.index)
                session = newState
                appendGmLogCoalescing(
                    "Комната (${pos.x + 1};${pos.y + 1}): подпись",
                    "Комната (${pos.x + 1};${pos.y + 1}): подпись → «${action.label.take(24)}»",
                )
                broadcastGame()
                return
            }
            is ClientMsg.GmAction.SetRoomRevealed -> {
                newState = GameLogic.updateRoom(newState, action.index) { it.copy(revealed = action.revealed) }
                logText = "Комната ${roomName(action.index)}: цвет ${if (action.revealed) "показан игрокам" else "скрыт"}"
            }
            is ClientMsg.GmAction.SetRoomNote -> {
                newState = GameLogic.updateRoom(newState, action.index) { it.copy(gmNoteId = action.noteId) }
                gmOnly = true
            }
            is ClientMsg.GmAction.UpsertGmNote -> {
                newState = GameLogic.upsertGmNote(newState, action.note)
                gmOnly = true
            }
            is ClientMsg.GmAction.DeleteGmNote -> {
                newState = GameLogic.deleteGmNote(newState, action.noteId)
                logText = "Заметка удалена"
                gmOnly = true
            }
        }

        session = newState
        logText?.let { appendGmLog(it) }
        // действия, видимые только ГМу, шлём только ГМу
        if (gmOnly) sendGameTo(client) else broadcastGame()
    }

    // ---------- Листы персонажей ----------

    /** Рассылка снапшота ГМу и владельцу листа (остальные листы игрокам не видны). */
    private suspend fun broadcastSheet(playerName: String) {
        gmId?.let { clients[it] }?.let { sendGameTo(it) }
        clients.values.firstOrNull { it.info.name == playerName }?.let { sendGameTo(it) }
    }

    /** Править лист может ГМ или сам владелец. */
    private fun canEditSheet(client: Client, playerName: String): Boolean =
        client.id == gmId || client.info.name == playerName

    private suspend fun handleUpdateSheet(client: Client, msg: ClientMsg.UpdateSheet) {
        val s = session ?: return
        if (!canEditSheet(client, msg.playerName)) return
        val old = s.sheets[msg.playerName] ?: CharacterSheet()
        val sheet = old.copy(
            stats = msg.stats,
            inspiration = msg.inspiration,
        )
        session = s.copy(sheets = s.sheets + (msg.playerName to sheet))
        appendGmLog("Лист персонажа ${msg.playerName} обновлён")
        broadcastSheet(msg.playerName)
    }

    private suspend fun handleUpsertCard(client: Client, msg: ClientMsg.UpsertCard) {
        val s = session ?: return
        if (!canEditSheet(client, msg.playerName)) return
        val old = s.sheets[msg.playerName] ?: CharacterSheet()
        val card = msg.card.copy(
            title = msg.card.title.take(60),
            level = msg.card.level.coerceIn(0, MAX_CARD_LEVEL),
            description = msg.card.description.take(2000),
        )
        val cards = old.cards.toMutableList()
        val idx = cards.indexOfFirst { it.id == card.id }
        if (idx >= 0) cards[idx] = card else cards.add(card)
        session = s.copy(sheets = s.sheets + (msg.playerName to old.copy(cards = cards)))
        appendGmLog("Карточка «${card.title.ifBlank { card.type.displayName }}» у ${msg.playerName} ${if (idx >= 0) "изменена" else "создана"}")
        broadcastSheet(msg.playerName)
    }

    private suspend fun handleDeleteCard(client: Client, msg: ClientMsg.DeleteCard) {
        val s = session ?: return
        if (!canEditSheet(client, msg.playerName)) return
        val old = s.sheets[msg.playerName] ?: return
        session = s.copy(sheets = s.sheets + (msg.playerName to old.copy(cards = old.cards.filterNot { it.id == msg.cardId })))
        appendGmLog("Карточка у ${msg.playerName} удалена")
        broadcastSheet(msg.playerName)
    }

    private suspend fun handleMoveCard(client: Client, msg: ClientMsg.MoveCard) {
        val s = session ?: return
        val name = client.info.name
        val old = s.sheets[name] ?: return
        val idx = old.cards.indexOfFirst { it.id == msg.cardId }
        if (idx < 0 || msg.newIndex !in old.cards.indices) return
        val cards = old.cards.toMutableList()
        val card = cards.removeAt(idx)
        cards.add(msg.newIndex, card)
        session = s.copy(sheets = s.sheets + (name to old.copy(cards = cards)))
        broadcastSheet(name)
    }

    // ---------- Рассылка ----------

    private fun appendSystem(text: String) {
        session?.let { session = GameLogic.appendLog(it, LogEntry.System(text)) }
    }

    private fun appendGmLog(text: String) {
        session?.let { session = GameLogic.appendLog(it, LogEntry.GmAction(text)) }
    }

    private fun appendGmLogCoalescing(prefix: String, text: String) {
        session?.let { session = GameLogic.appendGmLogCoalescing(it, prefix, text) }
    }

    private suspend fun broadcastLobby() {
        val msg = ServerMsg.LobbyState(
            players = clients.values.map { it.info },
            hostId = hostId ?: return,
            gmId = gmId ?: return,
            saves = runCatching { saveStore.list() }.getOrDefault(emptyList()),
            port = port,
        )
        for (c in clients.values) runCatching { c.send(msg) }
    }

    private suspend fun broadcastGame() {
        val s = session ?: return
        val gm = gmId ?: return
        for (c in clients.values) {
            runCatching { c.send(ServerMsg.GameSnapshot(buildView(c, s, gm))) }
        }
    }

    private suspend fun sendGameTo(client: Client) {
        val s = session ?: return
        val gm = gmId ?: return
        runCatching { client.send(ServerMsg.GameSnapshot(buildView(client, s, gm))) }
    }

    private fun buildView(client: Client, s: SessionState, gm: String): GameView {
        val isGm = client.id == gm
        val rooms = s.rooms.mapIndexed { index, room ->
            RoomView(
                id = room.id,
                index = index,
                color = if (isGm || room.revealed) room.color else RoomColor.Default,
                // подписи комнат видит только ГМ
                label = if (isGm) room.label else "",
                revealed = room.revealed,
                gmNoteId = if (isGm) room.gmNoteId else null,
            )
        }
        val log = if (isGm) s.log else s.log.filter { it !is LogEntry.GmAction }
        val messages = if (isGm) s.messages else s.messages.filter { it.toPlayerId == client.id }
        // ГМу — все листы; игроку — только свой
        val sheets = if (isGm) s.sheets else s.sheets.filterKeys { it == client.info.name }
        return GameView(
            you = client.info,
            players = clients.values.map { it.info },
            gmId = gm,
            isGm = isGm,
            rooms = rooms,
            tokenPos = s.tokenPos,
            myPersonalNote = s.personalNotes[client.info.name] ?: "",
            gmNotes = if (isGm) s.gmNotes else null,
            log = log,
            messages = messages,
            sheets = sheets,
        )
    }

    // ---------- Отключение ----------

    private suspend fun onDisconnect(client: Client) {
        stateLock.withLock {
            if (clients.remove(client.id) == null) return@withLock
            if (session == null) {
                if (gmId == client.id) gmId = hostId
                broadcastLobby()
            } else {
                appendSystem("${client.info.name} отключился.")
                if (gmId == client.id) {
                    gmId = hostId
                    appendSystem("ГМ отключился — роль ГМа вернулась к хосту.")
                }
                broadcastGame()
            }
        }
    }
}
