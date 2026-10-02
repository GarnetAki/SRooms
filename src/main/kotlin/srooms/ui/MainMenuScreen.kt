package srooms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import srooms.client.GameClient
import srooms.persistence.LocalSettings

@Composable
fun MainMenuScreen(root: AppRootState, settings: LocalSettings) {
    var name by remember { mutableStateOf(settings.playerName) }
    var hostIp by remember { mutableStateOf(settings.lastHost) }
    var portText by remember { mutableStateOf(settings.lastPort.toString()) }
    val connection by root.client.connection.collectAsState()

    fun persist() {
        settings.playerName = name.trim()
        settings.lastHost = hostIp.trim()
        settings.lastPort = portText.toIntOrNull() ?: LocalSettings.DEFAULT_PORT
        settings.persist()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("SRooms", style = MaterialTheme.typography.displaySmall)
        Text("Настольные сессии через Radmin", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Ваше имя") },
            singleLine = true,
            modifier = Modifier.width(320.dp),
        )
        Spacer(Modifier.height(24.dp))

        val nameOk = name.isNotBlank()
        val busy = connection == GameClient.ConnectionState.Connecting

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = nameOk && !busy,
                onClick = {
                    persist()
                    root.host(name.trim(), portText.toIntOrNull() ?: LocalSettings.DEFAULT_PORT)
                },
                modifier = Modifier.width(220.dp),
            ) { Text("Создать лобби") }
        }
        Text(
            "Порт: ${portText.toIntOrNull() ?: LocalSettings.DEFAULT_PORT} — сообщите друзьям ваш IP из Radmin",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(Modifier.width(420.dp))
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hostIp,
                onValueChange = { hostIp = it.trim() },
                label = { Text("IP хоста (Radmin)") },
                singleLine = true,
                modifier = Modifier.width(220.dp),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                label = { Text("Порт") },
                singleLine = true,
                modifier = Modifier.width(100.dp),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                enabled = nameOk && hostIp.isNotBlank() && !busy,
                onClick = {
                    persist()
                    root.client.connect(hostIp.trim(), portText.toIntOrNull() ?: LocalSettings.DEFAULT_PORT, name.trim())
                },
            ) { Text("Войти") }
        }

        if (busy) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        if (connection == GameClient.ConnectionState.Failed) {
            Spacer(Modifier.height(8.dp))
            Text("Подключение не удалось", color = MaterialTheme.colorScheme.error)
        }
    }
}
