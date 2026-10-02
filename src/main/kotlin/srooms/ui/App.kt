package srooms.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import srooms.client.GameClient
import srooms.core.PlayerMessage
import srooms.persistence.LocalSettings
import srooms.server.GameServer

class AppRootState(val settings: LocalSettings) {
    val client = GameClient()
    var server: GameServer? = null

    /** Ошибки запуска хоста (занятые порты и т.п.) — показываем снackbar'ом. */
    val hostErrors = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Пробуем запрошенный порт и девять следующих; сообщаем реальный через лобби. */
    fun host(name: String, port: Int) {
        stopHosting()
        for (p in port until port + PORT_ATTEMPTS) {
            try {
                val srv = GameServer(p)
                srv.start()
                server = srv
                if (p != port) hostErrors.tryEmit("Порт $port занят — использую порт $p")
                client.connect("127.0.0.1", p, name)
                return
            } catch (_: GameServer.PortInUseException) {
                // пробуем следующий порт
            } catch (e: Exception) {
                hostErrors.tryEmit("Не удалось запустить сервер: ${e.message}")
                return
            }
        }
        hostErrors.tryEmit(
            "Порты $port–${port + PORT_ATTEMPTS - 1} заняты. " +
                "Закройте лишние копии приложения или укажите другой порт."
        )
    }

    fun stopHosting() {
        server?.stop()
        server = null
    }

    companion object {
        const val PORT_ATTEMPTS = 10
    }
}

@Composable
fun App() {
    val settings = remember { LocalSettings() }
    val root = remember { AppRootState(settings) }
    val client = root.client

    val lobby by client.lobby.collectAsState()
    val game by client.game.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val popups = remember { mutableStateListOf<PlayerMessage>() }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(client) {
        client.events.collect { event ->
            when (event) {
                is GameClient.Event.Error -> scope.launch { snackbar.showSnackbar(event.text) }
                is GameClient.Event.Popup -> popups.add(event.message)
                is GameClient.Event.SessionEnded -> scope.launch { snackbar.showSnackbar("Сессия завершена") }
                is GameClient.Event.Disconnected -> scope.launch { snackbar.showSnackbar("Соединение разорвано") }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(root) {
        root.hostErrors.collect { scope.launch { snackbar.showSnackbar(it) } }
    }

    SRoomsTheme {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val gameView = game
                val lobbyView = lobby
                when {
                    gameView != null -> GameScreen(
                        view = gameView,
                        client = client,
                        onLeave = {
                            client.disconnect()
                            root.stopHosting()
                        },
                    )

                    lobbyView != null -> LobbyScreen(
                        view = lobbyView,
                        client = client,
                        onLeave = {
                            client.disconnect()
                            root.stopHosting()
                        },
                    )

                    else -> MainMenuScreen(
                        root = root,
                        settings = settings,
                    )
                }
            }
        }

        popups.firstOrNull()?.let { message ->
            AlertDialog(
                onDismissRequest = { popups.removeAt(0) },
                title = { Text("Послание от мастера") },
                text = { Text(message.text) },
                confirmButton = {
                    Button(onClick = { popups.removeAt(0) }) { Text("Принято") }
                },
            )
        }
    }
}
