package srooms.core

import kotlin.random.Random

object GameLogic {

    private val rollRegex = Regex("""^/roll\s+(\d+)\s*d\s*(\d+)$""")

    data class RollRequest(val count: Int, val sides: Int)

    /** Парсит "/roll 6 d 8" и "/roll 6d8". null — не команда броска. */
    fun parseRoll(input: String): RollRequest? {
        val m = rollRegex.matchEntire(input.trim()) ?: return null
        val count = m.groupValues[1].toIntOrNull() ?: return null
        val sides = m.groupValues[2].toIntOrNull() ?: return null
        if (count !in 1..100 || sides !in 2..1000) return null
        return RollRequest(count, sides)
    }

    fun roll(req: RollRequest, random: Random = Random): List<Int> =
        List(req.count) { random.nextInt(1, req.sides + 1) }

    fun swapRooms(state: SessionState, indexA: Int, indexB: Int): SessionState {
        if (indexA == indexB || indexA !in state.rooms.indices || indexB !in state.rooms.indices) return state
        val rooms = state.rooms.toMutableList()
        val tmp = rooms[indexA]
        rooms[indexA] = rooms[indexB]
        rooms[indexB] = tmp
        return state.copy(rooms = rooms)
    }

    /** Циклический сдвиг ряда. dir=+1 — вправо, dir=-1 — влево. */
    fun shiftRow(state: SessionState, row: Int, dir: Int): SessionState {
        if (row !in 0 until BOARD_SIZE) return state
        val rooms = state.rooms.toMutableList()
        val base = row * BOARD_SIZE
        val rowRooms = (0 until BOARD_SIZE).map { rooms[base + it] }
        val shift = ((dir % BOARD_SIZE) + BOARD_SIZE) % BOARD_SIZE
        for (x in 0 until BOARD_SIZE) {
            rooms[base + (x + shift) % BOARD_SIZE] = rowRooms[x]
        }
        var token = state.tokenPos
        if (token.y == row) {
            token = Pos((token.x + shift) % BOARD_SIZE, token.y)
        }
        return state.copy(rooms = rooms, tokenPos = token)
    }

    /** Циклический сдвиг столбца. dir=+1 — вниз, dir=-1 — вверх. */
    fun shiftCol(state: SessionState, col: Int, dir: Int): SessionState {
        if (col !in 0 until BOARD_SIZE) return state
        val rooms = state.rooms.toMutableList()
        val colRooms = (0 until BOARD_SIZE).map { rooms[it * BOARD_SIZE + col] }
        val shift = ((dir % BOARD_SIZE) + BOARD_SIZE) % BOARD_SIZE
        for (y in 0 until BOARD_SIZE) {
            rooms[((y + shift) % BOARD_SIZE) * BOARD_SIZE + col] = colRooms[y]
        }
        var token = state.tokenPos
        if (token.x == col) {
            token = Pos(token.x, (token.y + shift) % BOARD_SIZE)
        }
        return state.copy(rooms = rooms, tokenPos = token)
    }

    fun moveToken(state: SessionState, pos: Pos): SessionState {
        if (pos.x !in 0 until BOARD_SIZE || pos.y !in 0 until BOARD_SIZE) return state
        return state.copy(tokenPos = pos)
    }

    fun updateRoom(state: SessionState, index: Int, transform: (Room) -> Room): SessionState {
        if (index !in state.rooms.indices) return state
        val rooms = state.rooms.toMutableList()
        rooms[index] = transform(rooms[index])
        return state.copy(rooms = rooms)
    }

    fun appendLog(state: SessionState, entry: LogEntry): SessionState {
        val stamped = when (entry) {
            is LogEntry.System -> entry.copy(timestamp = now())
            is LogEntry.Chat -> entry.copy(timestamp = now())
            is LogEntry.Roll -> entry.copy(timestamp = now())
            is LogEntry.GmAction -> entry.copy(timestamp = now())
        }
        return state.copy(log = (state.log + stamped).takeLast(MAX_LOG_ENTRIES))
    }

    /** Добавляет GM-запись, но если последняя запись в логе — GM-действие с тем же префиксом
     *  (например, повторное изменение подписи той же комнаты), заменяет её вместо добавления. */
    fun appendGmLogCoalescing(state: SessionState, prefix: String, text: String): SessionState {
        val last = state.log.lastOrNull()
        return if (last is LogEntry.GmAction && last.text.startsWith(prefix)) {
            state.copy(log = state.log.dropLast(1) + last.copy(text = text))
        } else {
            appendLog(state, LogEntry.GmAction(text))
        }
    }

    fun upsertGmNote(state: SessionState, note: GmNote): SessionState {
        val notes = state.gmNotes.toMutableList()
        val idx = notes.indexOfFirst { it.id == note.id }
        if (idx >= 0) notes[idx] = note else notes.add(note)
        return state.copy(gmNotes = notes)
    }

    fun deleteGmNote(state: SessionState, noteId: String): SessionState {
        val rooms = state.rooms.map { if (it.gmNoteId == noteId) it.copy(gmNoteId = null) else it }
        return state.copy(gmNotes = state.gmNotes.filterNot { it.id == noteId }, rooms = rooms)
    }

    fun appendMessage(state: SessionState, message: PlayerMessage): SessionState =
        state.copy(messages = state.messages + message.copy(timestamp = now()))

    private fun now(): Long = System.currentTimeMillis()

    const val MAX_LOG_ENTRIES = 500
}
