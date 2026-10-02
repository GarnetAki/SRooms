package srooms.core

import kotlinx.serialization.Serializable

const val BOARD_SIZE = 5
const val ROOM_COUNT = BOARD_SIZE * BOARD_SIZE

enum class RoomColor(val displayName: String) {
    Default("Серый"),
    White("Белый"),
    Green("Зелёный"),
    Yellow("Жёлтый"),
    Blue("Синий"),
    Red("Красный"),
    Purple("Фиолетовый"),
    Orange("Оранжевый"),
}

enum class PlayerColor(val displayName: String) {
    Red("Красный"),
    Coral("Коралловый"),
    Orange("Оранжевый"),
    Amber("Янтарный"),
    Yellow("Жёлтый"),
    Lime("Лаймовый"),
    Green("Зелёный"),
    Emerald("Изумрудный"),
    Teal("Морской"),
    Cyan("Бирюзовый"),
    Sky("Небесный"),
    Blue("Синий"),
    Indigo("Индиго"),
    Violet("Фиолетовый"),
    Purple("Пурпурный"),
    Magenta("Маджента"),
    Pink("Розовый"),
    Rose("Розово-красный"),
    Brown("Коричневый"),
    Silver("Серебряный"),
    Gold("Золотой"),
    White("Белый"),
    Gray("Серый"),
    Black("Чёрный"),
}

@Serializable
data class Pos(val x: Int, val y: Int) {
    fun index(): Int = y * BOARD_SIZE + x

    companion object {
        fun of(index: Int) = Pos(index % BOARD_SIZE, index / BOARD_SIZE)
    }
}

@Serializable
data class Room(
    val id: String,
    val color: RoomColor = RoomColor.Default,
    val label: String = "",
    val gmNoteId: String? = null,
    val revealed: Boolean = false,
)

@Serializable
data class PlayerInfo(
    val id: String,
    val name: String,
    val color: PlayerColor? = null,
    val connected: Boolean = true,
)

@Serializable
data class GmNote(
    val id: String,
    val title: String,
    val text: String,
)

@Serializable
sealed interface LogEntry {
    val timestamp: Long

    @Serializable
    data class System(val text: String, override val timestamp: Long = 0L) : LogEntry

    @Serializable
    data class Chat(
        val playerId: String,
        val playerName: String,
        val color: PlayerColor?,
        val text: String,
        override val timestamp: Long = 0L,
    ) : LogEntry

    @Serializable
    data class Roll(
        val playerId: String,
        val playerName: String,
        val color: PlayerColor?,
        val count: Int,
        val sides: Int,
        val results: List<Int>,
        override val timestamp: Long = 0L,
    ) : LogEntry

    /** Виден только ГМу. */
    @Serializable
    data class GmAction(val text: String, override val timestamp: Long = 0L) : LogEntry
}

@Serializable
data class PlayerMessage(
    val id: String,
    val toPlayerId: String,
    val toPlayerName: String,
    val text: String,
    val timestamp: Long = 0L,
)

// ---------- Лист персонажа (Витрувий) ----------

enum class Stat(val displayName: String) {
    Physique("Телосложение"),
    Attention("Внимание"),
    Movement("Движение"),
    Mind("Мышление"),
    Social("Общение"),
    Will("Воля"),
}

enum class CardType(val displayName: String) {
    Skill("Навык"),
    Item("Предмет"),
}

@Serializable
data class CharCard(
    val id: String,
    val type: CardType = CardType.Skill,
    val title: String = "",
    /** Уровень 0..5 (для навыков; у предметов игнорируется). */
    val level: Int = 0,
    val description: String = "",
)

@Serializable
data class CharacterSheet(
    val stats: Map<Stat, Int> = emptyMap(),
    val inspiration: Int = 0,
    val cards: List<CharCard> = emptyList(),
)

fun CharacterSheet.stat(stat: Stat): Int = stats[stat] ?: 0

const val MAX_CARD_LEVEL = 5

@Serializable
data class SessionState(
    val rooms: List<Room> = (1..ROOM_COUNT).map { Room(id = "room-$it") },
    val tokenPos: Pos = Pos(0, 0),
    val gmNotes: List<GmNote> = emptyList(),
    /** playerName -> текст личной заметки (в сейвах ключ — имя, т.к. id меняются между сессиями). */
    val personalNotes: Map<String, String> = emptyMap(),
    /** playerName -> лист персонажа. */
    val sheets: Map<String, CharacterSheet> = emptyMap(),
    val log: List<LogEntry> = emptyList(),
    val messages: List<PlayerMessage> = emptyList(),
)

/** Снапшот сессии на диске. */
@Serializable
data class SessionSave(
    val name: String,
    val savedAt: Long,
    val state: SessionState,
)
