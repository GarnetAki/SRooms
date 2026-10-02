package srooms.persistence

import java.io.File
import java.util.Properties

/** Локальные настройки UI: имя, последний адрес подключения. */
class LocalSettings(
    private val file: File = File(System.getProperty("user.home"), ".srooms/config.properties"),
) {
    var playerName: String = ""
    var lastHost: String = ""
    var lastPort: Int = DEFAULT_PORT

    init {
        runCatching {
            if (file.exists()) {
                val props = Properties()
                file.inputStream().use { props.load(it) }
                playerName = props.getProperty("playerName", "")
                lastHost = props.getProperty("lastHost", "")
                lastPort = props.getProperty("lastPort")?.toIntOrNull() ?: DEFAULT_PORT
            }
        }
    }

    fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties()
            props.setProperty("playerName", playerName)
            props.setProperty("lastHost", lastHost)
            props.setProperty("lastPort", lastPort.toString())
            file.outputStream().use { props.store(it, "SRooms") }
        }
    }

    companion object {
        const val DEFAULT_PORT = 25575
    }
}
