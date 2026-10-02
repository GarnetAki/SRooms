package srooms.protocol

import kotlinx.serialization.Serializable
import srooms.core.CharCard
import srooms.core.CharacterSheet
import srooms.core.GmNote
import srooms.core.LogEntry
import srooms.core.PlayerColor
import srooms.core.PlayerInfo
import srooms.core.PlayerMessage
import srooms.core.Pos
import srooms.core.Room
import srooms.core.RoomColor
import srooms.core.Stat

// ---------- Клиент -> Сервер ----------

@Serializable
sealed interface ClientMsg {
    @Serializable
    data class Join(val name: String) : ClientMsg

    @Serializable
    data class SetColor(val color: PlayerColor) : ClientMsg

    /** Только хост: назначить ГМа (null — вернуть ГМа хосту). */
    @Serializable
    data class AssignGm(val playerId: String) : ClientMsg

    /** Только хост: начать сессию (saveName == null — новая). */
    @Serializable
    data class StartSession(val saveName: String? = null) : ClientMsg

    /** Только хост: завершить сессию и вернуть всех в лобби. */
    @Serializable
    data object ReturnToLobby : ClientMsg

    /** Текст в панели логов: чат или команда /roll. */
    @Serializable
    data class LogInput(val text: String) : ClientMsg

    @Serializable
    data class UpdatePersonalNote(val text: String) : ClientMsg

    /** Только ГМ: личное послание игроку. */
    @Serializable
    data class SendPlayerMessage(val toPlayerId: String, val text: String) : ClientMsg

    /** Только ГМ: сохранить сессию под именем. */
    @Serializable
    data class SaveSession(val name: String) : ClientMsg

    /** Только ГМ: задать статы и вдохновения игрока (playerName). */
    @Serializable
    data class UpdateSheet(
        val playerName: String,
        val stats: Map<Stat, Int>,
        val inspiration: Int,
    ) : ClientMsg

    /** Только ГМ: создать/изменить карточку игрока. Позиция в списке сохраняется. */
    @Serializable
    data class UpsertCard(val playerName: String, val card: CharCard) : ClientMsg

    /** Только ГМ: удалить карточку игрока. */
    @Serializable
    data class DeleteCard(val playerName: String, val cardId: String) : ClientMsg

    /** Игрок: переставить СВОЮ карточку на новую позицию. */
    @Serializable
    data class MoveCard(val cardId: String, val newIndex: Int) : ClientMsg

    /** Действия ГМа с доской и заметками. */
    @Serializable
    sealed interface GmAction : ClientMsg {
        @Serializable
        data class MoveToken(val pos: Pos) : GmAction

        @Serializable
        data class SwapRooms(val indexA: Int, val indexB: Int) : GmAction

        @Serializable
        data class ShiftRow(val row: Int, val dir: Int) : GmAction

        @Serializable
        data class ShiftCol(val col: Int, val dir: Int) : GmAction

        @Serializable
        data class SetRoomColor(val index: Int, val color: RoomColor) : GmAction

        @Serializable
        data class SetRoomLabel(val index: Int, val label: String) : GmAction

        @Serializable
        data class SetRoomRevealed(val index: Int, val revealed: Boolean) : GmAction

        /** Привязать заметку к комнате (noteId == null — отвязать). */
        @Serializable
        data class SetRoomNote(val index: Int, val noteId: String?) : GmAction

        @Serializable
        data class UpsertGmNote(val note: GmNote) : GmAction

        @Serializable
        data class DeleteGmNote(val noteId: String) : GmAction
    }
}

// ---------- Сервер -> Клиент ----------

@Serializable
sealed interface ServerMsg {
    @Serializable
    data class Error(val text: String) : ServerMsg

    /** Подтверждение входа в лобби: выдаёт id подключившемуся. */
    @Serializable
    data class Welcome(val playerId: String, val isHost: Boolean) : ServerMsg

    @Serializable
    data class LobbyState(
        val players: List<PlayerInfo>,
        val hostId: String,
        val gmId: String,
        val saves: List<String>,
        val port: Int,
    ) : ServerMsg

    /** Персональный срез игрового состояния. */
    @Serializable
    data class GameSnapshot(val view: GameView) : ServerMsg

    /** Личное послание от ГМа — у игрока всплывает попап. */
    @Serializable
    data class PlayerMessageEvent(val message: PlayerMessage) : ServerMsg

    /** Хост завершил сессию / сессия прервана — все возвращаются в лобби. */
    @Serializable
    data object SessionEnded : ServerMsg
}

/** Комната в персональном виде: цвет уже «отфильтрован» под роль. */
@Serializable
data class RoomView(
    val id: String,
    val index: Int,
    val color: RoomColor,
    val label: String,
    val revealed: Boolean,
    /** Только у ГМа. */
    val gmNoteId: String? = null,
) {
    val hasGmNote: Boolean get() = gmNoteId != null
}

@Serializable
data class GameView(
    val you: PlayerInfo,
    val players: List<PlayerInfo>,
    val gmId: String,
    val isGm: Boolean,
    val rooms: List<RoomView>,
    val tokenPos: Pos,
    val myPersonalNote: String,
    /** null у игроков. */
    val gmNotes: List<GmNote>? = null,
    /** Уже отфильтрован: игрокам без GmAction. */
    val log: List<LogEntry>,
    /** Игроку — его послания; ГМу — все. */
    val messages: List<PlayerMessage>,
    /** ГМу — листы всех игроков; игроку — только свой (ключ — имя игрока). */
    val sheets: Map<String, CharacterSheet> = emptyMap(),
)
