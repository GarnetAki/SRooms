package srooms.persistence

import kotlinx.serialization.json.Json
import srooms.core.LogEntry
import srooms.core.SessionSave
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveStore(
    private val dir: File = File(System.getProperty("user.home"), ".srooms/saves"),
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        dir.mkdirs()
    }

    private fun safeName(name: String): String {
        val safe = name.trim().replace(Regex("""[^\p{L}\p{N} _-]"""), "_")
        require(safe.isNotBlank()) { "Пустое имя сейва" }
        return safe
    }

    private fun fileFor(name: String) = File(dir, "${safeName(name)}.json")

    private fun logFileFor(name: String) = File(dir, "${safeName(name)}.log.json")

    private fun logTextFileFor(name: String) = File(dir, "${safeName(name)}.log.txt")

    fun list(): List<String> =
        dir.listFiles { f -> f.extension == "json" && !f.name.endsWith(".log.json") }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString<SessionSave>(f.readText()).name }
                    .getOrElse { f.nameWithoutExtension }
            }
            ?.sorted()
            .orEmpty()

    /** Сейв пишется в `<имя>.json`, лог — отдельно в `<имя>.log.json` (машинный)
     *  и `<имя>.log.txt` (человекочитаемый). */
    fun save(save: SessionSave) {
        fileFor(save.name).writeText(
            json.encodeToString(save.copy(state = save.state.copy(log = emptyList())))
        )
        logFileFor(save.name).writeText(json.encodeToString(save.state.log))
        logTextFileFor(save.name).writeText(save.state.log.joinToString("\n") { renderEntry(it) })
    }

    fun load(name: String): SessionSave {
        val save = json.decodeFromString<SessionSave>(fileFor(name).readText())
        val logFile = logFileFor(name)
        val log = if (logFile.exists()) {
            runCatching { json.decodeFromString<List<LogEntry>>(logFile.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return save.copy(state = save.state.copy(log = log))
    }

    fun delete(name: String) {
        fileFor(name).delete()
        logFileFor(name).delete()
        logTextFileFor(name).delete()
    }

    private fun renderEntry(entry: LogEntry): String {
        val time = if (entry.timestamp > 0) "[${timeFormat.format(Date(entry.timestamp))}] " else ""
        return when (entry) {
            is LogEntry.System -> "$time— ${entry.text}"
            is LogEntry.Chat -> "$time${entry.playerName}: ${entry.text}"
            is LogEntry.Roll ->
                "$time${entry.playerName} → бросок ${entry.count}d${entry.sides}: " +
                    "${entry.results.joinToString(", ")} (Σ = ${entry.results.sum()})"
            is LogEntry.GmAction -> "$time[ГМ] ${entry.text}"
        }
    }
}
