package srooms.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import srooms.core.PlayerMessage
import srooms.protocol.ClientMsg
import srooms.protocol.GameView
import srooms.protocol.ServerMsg
import srooms.protocol.protocolJson

class GameClient {
    enum class ConnectionState { Disconnected, Connecting, Connected, Failed }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connection = MutableStateFlow(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection

    /** Ид и флаг хоста после Welcome. */
    private val _myId = MutableStateFlow<String?>(null)
    val myId: StateFlow<String?> = _myId
    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost

    private val _lobby = MutableStateFlow<ServerMsg.LobbyState?>(null)
    val lobby: StateFlow<ServerMsg.LobbyState?> = _lobby

    private val _game = MutableStateFlow<GameView?>(null)
    val game: StateFlow<GameView?> = _game

    /** Разовые события для UI: ошибки, попап-послания, конец сессии. */
    sealed interface Event {
        data class Error(val text: String) : Event
        data class Popup(val message: PlayerMessage) : Event
        data object SessionEnded : Event
        data object Disconnected : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events

    private var http: HttpClient? = null
    private var sendChannel: suspend (ClientMsg) -> Unit = {}
    private var job: Job? = null

    fun connect(host: String, port: Int, name: String) {
        disconnect(silent = true)
        _connection.value = ConnectionState.Connecting
        val client = HttpClient(CIO) { install(WebSockets) }
        http = client
        job = scope.launch {
            try {
                client.webSocket(host = host, port = port, path = "/ws") {
                    sendChannel = { msg ->
                        outgoing.send(Frame.Text(protocolJson.encodeToString(ClientMsg.serializer(), msg)))
                    }
                    sendChannel(ClientMsg.Join(name))
                    _connection.value = ConnectionState.Connected
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val msg = runCatching {
                            protocolJson.decodeFromString(ServerMsg.serializer(), frame.readText())
                        }.getOrNull() ?: continue
                        dispatch(msg)
                    }
                }
                // нормальное закрытие
                if (_connection.value == ConnectionState.Connected || _connection.value == ConnectionState.Connecting) {
                    _events.tryEmit(Event.Disconnected)
                }
            } catch (e: Exception) {
                if (_connection.value == ConnectionState.Connecting) {
                    _connection.value = ConnectionState.Failed
                    _events.tryEmit(Event.Error("Не удалось подключиться к $host:$port — ${e.message ?: "нет соединения"}"))
                } else {
                    _events.tryEmit(Event.Disconnected)
                }
            } finally {
                _connection.value = ConnectionState.Disconnected
                _lobby.value = null
                _game.value = null
                client.close()
            }
        }
    }

    private suspend fun dispatch(msg: ServerMsg) {
        when (msg) {
            is ServerMsg.Error -> _events.emit(Event.Error(msg.text))
            is ServerMsg.Welcome -> {
                _myId.value = msg.playerId
                _isHost.value = msg.isHost
            }
            is ServerMsg.LobbyState -> _lobby.value = msg
            is ServerMsg.GameSnapshot -> {
                _lobby.value = null
                _game.value = msg.view
            }
            is ServerMsg.PlayerMessageEvent -> _events.emit(Event.Popup(msg.message))
            is ServerMsg.SessionEnded -> {
                _game.value = null
                _events.emit(Event.SessionEnded)
            }
        }
    }

    fun send(msg: ClientMsg) {
        val ch = sendChannel
        scope.launch {
            runCatching { ch(msg) }
        }
    }

    fun disconnect(silent: Boolean = false) {
        job?.cancel()
        job = null
        http?.close()
        http = null
        sendChannel = {}
        _connection.value = ConnectionState.Disconnected
        _myId.value = null
        _isHost.value = false
        _lobby.value = null
        _game.value = null
        if (!silent) _events.tryEmit(Event.Disconnected)
    }

    fun close() {
        disconnect(silent = true)
        scope.cancel()
    }
}
