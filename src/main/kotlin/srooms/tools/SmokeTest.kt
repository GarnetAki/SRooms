package srooms.tools

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import srooms.client.GameClient
import srooms.core.PlayerColor
import srooms.core.Pos
import srooms.core.RoomColor
import srooms.core.stat
import srooms.protocol.ClientMsg
import srooms.server.GameServer
import kotlin.system.exitProcess

/**
 * Смоук-тест без UI: поднимает сервер, подключает хоста и игрока,
 * гоняет основной сценарий. Запуск: gradlew runSmokeTest (или main() из IDEA).
 */
fun main(): Unit = runBlocking {
    val port = 27777
    val server = GameServer(port)
    server.start()
    delay(500)

    var failures = 0
    fun check(cond: Boolean, name: String) {
        if (cond) println("  OK: $name") else {
            println("  FAIL: $name")
            failures++
        }
    }

    val host = GameClient()
    val player = GameClient()

    try {
        println("== Подключение ==")
        host.connect("127.0.0.1", port, "Хост")
        // ждём, пока хост точно станет первым в лобби, и только потом подключаем игрока
        val hostLobby = withTimeout(5000) { host.lobby.first { it != null } }!!
        player.connect("127.0.0.1", port, "Игрок")
        val playerLobby = withTimeout(5000) { player.lobby.first { l -> l?.players?.size == 2 } }!!
        check(hostLobby.hostId == host.myId.value, "хост назначен хостом")
        check(hostLobby.gmId == hostLobby.hostId, "хост — ГМ по умолчанию")
        check(playerLobby.players.size == 2, "игрок видит обоих в лобби")
        check(
            playerLobby.players.all { it.color != null } &&
                playerLobby.players.map { it.color }.toSet().size == 2,
            "цвета выданы автоматически и без повторов",
        )

        println("== Цвета и ГМ ==")
        host.send(ClientMsg.SetColor(PlayerColor.Red))
        player.send(ClientMsg.SetColor(PlayerColor.Red)) // занят — должен отказать
        delay(300)
        player.send(ClientMsg.SetColor(PlayerColor.Teal))
        val lobby2 = withTimeout(5000) {
            player.lobby.first { l ->
                l?.players?.firstOrNull { it.id == player.myId.value }?.color == PlayerColor.Teal
            }
        }!!
        check(lobby2.players.first { it.id == host.myId.value }.color == PlayerColor.Red, "цвет хоста = красный")
        check(lobby2.players.first { it.id == player.myId.value }.color == PlayerColor.Teal, "игрок получил морской (красный был занят)")

        // назначить игрока ГМом и вернуть обратно
        host.send(ClientMsg.AssignGm(player.myId.value!!))
        val lobby3 = withTimeout(5000) { host.lobby.first { it?.gmId == player.myId.value } }!!
        check(lobby3.gmId == player.myId.value, "хост назначил игрока ГМом")
        host.send(ClientMsg.AssignGm(host.myId.value!!))
        withTimeout(5000) { host.lobby.first { it?.gmId == host.myId.value } }

        // дубликат имени должен быть отклонён (имя — ключ заметок и листа)
        val dup = GameClient()
        dup.connect("127.0.0.1", port, "Игрок")
        val dupRejected = withTimeout(5000) { dup.events.first { it is GameClient.Event.Error } }
        check(dupRejected is GameClient.Event.Error, "дубликат имени отклонён")
        dup.close()

        println("== Старт сессии ==")
        host.send(ClientMsg.StartSession(null))
        val hostView = withTimeout(5000) { host.game.first { it != null } }!!
        val playerView = withTimeout(5000) { player.game.first { it != null } }!!
        check(hostView.isGm, "хост видит себя ГМом")
        check(!playerView.isGm, "игрок не ГМ")
        check(playerView.gmNotes == null, "игроку не пришли GM-заметки")

        println("== Доска ==")
        host.send(ClientMsg.GmAction.SetRoomColor(0, RoomColor.Red))
        host.send(ClientMsg.GmAction.SetRoomLabel(0, "Таверна"))
        host.send(ClientMsg.GmAction.MoveToken(Pos(2, 2)))
        delay(400)
        val pv1 = player.game.value!!
        check(pv1.rooms[0].color == RoomColor.Default, "игрок видит скрытую комнату серой")
        check(pv1.rooms[0].label.isEmpty(), "игрок НЕ видит подпись комнаты")
        check(pv1.tokenPos == Pos(2, 2), "фишка переместилась у игрока")
        val hv1 = host.game.value!!
        check(hv1.rooms[0].color == RoomColor.Red, "ГМ видит реальный цвет")
        check(hv1.rooms[0].label == "Таверна", "ГМ видит подпись комнаты")

        host.send(ClientMsg.GmAction.SetRoomRevealed(0, true))
        val pv2 = withTimeout(5000) { player.game.first { it?.rooms?.get(0)?.revealed == true } }!!
        check(pv2.rooms[0].color == RoomColor.Red, "после reveal игрок видит красный")

        // swap и сдвиги
        host.send(ClientMsg.GmAction.SetRoomColor(1, RoomColor.Green))
        delay(300)
        host.send(ClientMsg.GmAction.SwapRooms(0, 1))
        val hv2 = withTimeout(5000) { host.game.first { it?.rooms?.get(0)?.color == RoomColor.Green } }!!
        check(hv2.rooms[1].color == RoomColor.Red, "swap комнат сработал")
        host.send(ClientMsg.GmAction.ShiftRow(0, 1))
        val hv3 = withTimeout(5000) { host.game.first { it?.rooms?.get(1)?.color == RoomColor.Green } }!!
        check(hv3.rooms[2].color == RoomColor.Red, "сдвиг ряда сработал")
        host.send(ClientMsg.GmAction.ShiftCol(1, 1))
        val hv4 = withTimeout(5000) { host.game.first { it?.rooms?.get(6)?.color == RoomColor.Green } }!!
        check(hv4.rooms[1].color == RoomColor.Default, "сдвиг столбца сработал")

        // подпись, меняемая посимвольно, должна склеиваться в одну запись GM-лога
        host.send(ClientMsg.GmAction.SetRoomLabel(5, "Тав"))
        host.send(ClientMsg.GmAction.SetRoomLabel(5, "Тавер"))
        host.send(ClientMsg.GmAction.SetRoomLabel(5, "Таверна"))
        val hvLog = withTimeout(5000) { host.game.first { it?.rooms?.get(5)?.label == "Таверна" } }!!
        val labelEntries = hvLog.log.filterIsInstance<srooms.core.LogEntry.GmAction>()
            .count { it.text.startsWith("Комната (1;2): подпись") }
        check(labelEntries == 1, "посимвольные изменения подписи склеены в одну запись (было бы 3)")

        println("== Логи и кубы ==")
        player.send(ClientMsg.LogInput("Привет всем!"))
        player.send(ClientMsg.LogInput("/roll 6 d 8"))
        val pv3 = withTimeout(5000) {
            player.game.first { v -> v?.log?.any { it is srooms.core.LogEntry.Roll } == true }
        }!!
        val roll = pv3.log.filterIsInstance<srooms.core.LogEntry.Roll>().last()
        check(roll.count == 6 && roll.sides == 8 && roll.results.size == 6, "кубы 6d8: 6 результатов")
        check(roll.results.all { it in 1..8 }, "значения в диапазоне 1..8")
        check(roll.playerName == "Игрок", "автор броска — игрок")

        // GM-логи видны только ГМу
        val gmLogCount = host.game.value!!.log.count { it is srooms.core.LogEntry.GmAction }
        val plLogCount = player.game.value!!.log.count { it is srooms.core.LogEntry.GmAction }
        check(gmLogCount > 0, "ГМ видит GM-логи ($gmLogCount)")
        check(plLogCount == 0, "игрок НЕ видит GM-логи")

        println("== Заметки ==")
        player.send(ClientMsg.UpdatePersonalNote("тайная запись игрока"))
        val pv4 = withTimeout(5000) { player.game.first { it?.myPersonalNote == "тайная запись игрока" } }
        check(true, "личная заметка игрока сохранена")
        host.send(ClientMsg.GmAction.UpsertGmNote(srooms.core.GmNote("note-1", "Секреты", "текст для ГМа")))
        withTimeout(5000) { host.game.first { v -> v?.gmNotes?.any { it.id == "note-1" } == true } }
        check(true, "GM-заметка сохранена")
        check(player.game.value!!.gmNotes == null, "игроку GM-заметки недоступны")

        println("== Послания ==")
        host.send(ClientMsg.SendPlayerMessage(player.myId.value!!, "Ты слышишь шорох за дверью"))
        val popup = withTimeout(5000) { player.events.first { it is GameClient.Event.Popup } }
        check(popup is GameClient.Event.Popup, "игроку пришёл попап")
        check((popup as? GameClient.Event.Popup)?.message?.text == "Ты слышишь шорох за дверью", "текст послания верный")

        println("== Персонажи ==")
        host.send(ClientMsg.UpdateSheet("Игрок", mapOf(srooms.core.Stat.Will to 3, srooms.core.Stat.Mind to 2), 2))
        val pv6 = withTimeout(5000) { player.game.first { it?.sheets?.get("Игрок")?.stat(srooms.core.Stat.Will) == 3 } }!!
        check(pv6.sheets.size == 1, "игрок видит только свой лист")
        check(pv6.sheets["Игрок"]!!.inspiration == 2, "вдохновения обновлены")
        // игрок МОЖЕТ править свой лист
        player.send(ClientMsg.UpdateSheet("Игрок", mapOf(srooms.core.Stat.Will to 4), 3))
        withTimeout(5000) { player.game.first { it?.sheets?.get("Игрок")?.stat(srooms.core.Stat.Will) == 4 } }
        check(true, "игрок может менять свои статы")
        // но НЕ чужой
        player.send(ClientMsg.UpdateSheet("Хост", mapOf(srooms.core.Stat.Will to 5), 9))
        delay(400)
        check(host.game.value!!.sheets["Хост"] == null, "игрок НЕ может менять чужой лист")
        // игрок может создавать свои карточки
        player.send(ClientMsg.UpsertCard("Игрок", srooms.core.CharCard("c0", srooms.core.CardType.Item, "Фляга")))
        withTimeout(5000) { player.game.first { v -> v?.sheets?.get("Игрок")?.cards?.any { it.id == "c0" } == true } }
        check(true, "игрок создал свою карточку")
        // карточки: ГМ создаёт, игрок переставляет, ГМ редактирует с сохранением позиции
        host.send(ClientMsg.UpsertCard("Игрок", srooms.core.CharCard("c1", srooms.core.CardType.Skill, "Маскировка", 3, "текст")))
        host.send(ClientMsg.UpsertCard("Игрок", srooms.core.CharCard("c2", srooms.core.CardType.Item, "Фонарик")))
        withTimeout(5000) { player.game.first { it?.sheets?.get("Игрок")?.cards?.size == 3 } }
        player.send(ClientMsg.MoveCard("c2", 0))
        withTimeout(5000) { player.game.first { it?.sheets?.get("Игрок")?.cards?.first()?.id == "c2" } }
        check(true, "игрок переставил карточку вверх")
        host.send(ClientMsg.UpsertCard("Игрок", srooms.core.CharCard("c2", srooms.core.CardType.Item, "Фонарик+")))
        val pv9 = withTimeout(5000) { player.game.first { it?.sheets?.get("Игрок")?.cards?.first()?.title == "Фонарик+" } }!!
        check(pv9.sheets["Игрок"]!!.cards.map { it.id } == listOf("c2", "c0", "c1"), "ГМ отредактировал карточку, позиции сохранились")

        println("== Сейв/лоад ==")
        host.send(ClientMsg.SaveSession("smoke-test"))
        withTimeout(5000) { host.game.first { v -> v?.log?.any { it is srooms.core.LogEntry.System && it.text.contains("smoke-test") } == true } }
        val logFile = java.io.File(System.getProperty("user.home"), ".srooms/saves/smoke-test.log.json")
        check(logFile.exists(), "лог сохранён отдельным файлом (smoke-test.log.json)")
        host.send(ClientMsg.ReturnToLobby)
        val lobby4 = withTimeout(5000) { host.lobby.first { it != null && "smoke-test" in it.saves } }!!
        check("smoke-test" in lobby4.saves, "сейв появился в списке")
        host.send(ClientMsg.StartSession("smoke-test"))
        val hv5 = withTimeout(5000) { host.game.first { it != null } }!!
        check(hv5.rooms[2].color == RoomColor.Red && hv5.rooms[6].color == RoomColor.Green, "сейв загрузился: цвета комнат на местах")
        val pv5 = withTimeout(5000) { player.game.first { it != null } }!!
        check(pv5.myPersonalNote == "тайная запись игрока", "личная заметка игрока подтянулась из сейва")
        check(pv5.log.any { it is srooms.core.LogEntry.Roll }, "лог подтянулся из отдельного файла")
        check(pv5.sheets["Игрок"]?.cards?.size == 3, "лист персонажа подтянулся из сейва")
        println("== Порты ==")
        val s1 = GameServer(27888)
        s1.start()
        val busyDetected = try {
            GameServer(27888).start()
            false
        } catch (e: GameServer.PortInUseException) {
            true
        }
        check(busyDetected, "повторный бинд на занятый порт кидает PortInUseException")
        s1.stop()
    } catch (e: Exception) {
        println("EXCEPTION: ${e.message}")
        e.printStackTrace()
        failures++
    } finally {
        host.close()
        player.close()
        server.stop()
    }

    println(if (failures == 0) "\nВСЕ ПРОВЕРКИ ПРОШЛИ" else "\nПРОВАЛОВ: $failures")
    exitProcess(if (failures == 0) 0 else 1)
}
