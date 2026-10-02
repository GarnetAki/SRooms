package srooms.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import srooms.client.GameClient
import srooms.protocol.ClientMsg
import srooms.protocol.GameView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun MessagesPanel(view: GameView, client: GameClient, modifier: Modifier = Modifier) {
    Column(modifier.padding(8.dp)) {
        if (view.isGm) {
            GmComposer(view, client)
            Spacer(Modifier.height(8.dp))
        } else {
            Text("Послания от мастера приходят сюда и всплывают поверх окна.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        val listState = rememberLazyListState()
        LaunchedEffect(view.messages.size) {
            if (view.messages.isNotEmpty()) listState.animateScrollToItem(view.messages.size - 1)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(view.messages) { msg ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Row {
                            Text(
                                if (view.isGm) "→ ${msg.toPlayerName}" else "От мастера",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (msg.timestamp > 0) timeFormat.format(Date(msg.timestamp)) else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun GmComposer(view: GameView, client: GameClient) {
    val targets = view.players.filter { it.id != view.you.id }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }

    if (targets.isEmpty()) {
        Text("Нет других игроков — некому слать послания.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val selected = targets.firstOrNull { it.id == selectedId } ?: targets.first().also { selectedId = it.id }

    Text("Кому:", style = MaterialTheme.typography.labelMedium)
    Row {
        targets.forEach { player ->
            FilterChip(
                selected = player.id == selected.id,
                onClick = { selectedId = player.id },
                label = { Text(player.name) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(2000) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Текст послания для ${selected.name}…") },
        minLines = 2,
    )
    Spacer(Modifier.height(4.dp))
    Button(
        enabled = text.isNotBlank(),
        onClick = {
            client.send(ClientMsg.SendPlayerMessage(selected.id, text.trim()))
            text = ""
        },
    ) { Text("Отправить") }
}
