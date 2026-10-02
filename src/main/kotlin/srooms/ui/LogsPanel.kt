package srooms.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import srooms.client.GameClient
import srooms.core.LogEntry
import srooms.protocol.ClientMsg
import srooms.protocol.GameView

@Composable
fun LogsPanel(view: GameView, client: GameClient, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(view.log.size) {
        if (view.log.isNotEmpty()) listState.animateScrollToItem(view.log.size - 1)
    }

    fun submit() {
        val text = input.trim()
        if (text.isNotEmpty()) {
            client.send(ClientMsg.LogInput(text))
            input = ""
        }
    }

    Column(modifier.padding(8.dp)) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(view.log) { entry ->
                LogEntryRow(entry)
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение или /roll 6 d 8") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { submit() }) { Text("→") }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val nameColor = when (entry) {
        is LogEntry.Chat -> entry.color?.toCompose()
        is LogEntry.Roll -> entry.color?.toCompose()
        else -> null
    } ?: MaterialTheme.colorScheme.onSurface

    val text = buildAnnotatedString {
        when (entry) {
            is LogEntry.System ->
                withStyle(SpanStyle(color = Color.Gray, fontStyle = FontStyle.Italic)) {
                    append("— ${entry.text}")
                }

            is LogEntry.Chat -> {
                withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) {
                    append("${entry.playerName}: ")
                }
                append(entry.text)
            }

            is LogEntry.Roll -> {
                withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) {
                    append(entry.playerName)
                }
                append(" → бросок ${entry.count}d${entry.sides}: ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(entry.results.joinToString(", "))
                }
                append("  (Σ = ${entry.results.sum()})")
            }

            is LogEntry.GmAction ->
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                    append("🔒 [только ГМ] ${entry.text}")
                }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
}
