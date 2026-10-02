package srooms.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import srooms.client.GameClient
import srooms.core.GmNote
import srooms.protocol.ClientMsg
import srooms.protocol.GameView
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun NotesPanel(
    view: GameView,
    client: GameClient,
    selectedNoteId: String?,
    onSelectNote: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (view.isGm) {
        GmNotesPanel(view, client, selectedNoteId, onSelectNote, modifier)
    } else {
        PersonalNotePanel(view, client, modifier)
    }
}

/** Личная заметка игрока. */
@Composable
private fun PersonalNotePanel(view: GameView, client: GameClient, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(view.myPersonalNote) }
    var lastSent by remember { mutableStateOf(view.myPersonalNote) }

    // Обновляем локальный текст только если он не расходится с тем, что мы сами отправили
    LaunchedEffect(view.myPersonalNote) {
        if (text == lastSent && view.myPersonalNote != text) {
            text = view.myPersonalNote
            lastSent = view.myPersonalNote
        }
    }
    // Дебаунс: отправка после паузы в печатании, а не на каждый символ
    LaunchedEffect(text) {
        if (text != view.myPersonalNote) {
            delay(400)
            client.send(ClientMsg.UpdatePersonalNote(text))
            lastSent = text
        }
    }

    Column(modifier.padding(8.dp)) {
        Text("Личные заметки (видны только вам)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxSize(),
            placeholder = { Text("Пишите что угодно: наблюдения, планы, подозрения…") },
        )
    }
}

/** Заметки мастера: список + редактор. */
@Composable
private fun GmNotesPanel(
    view: GameView,
    client: GameClient,
    selectedNoteId: String?,
    onSelectNote: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notes = view.gmNotes.orEmpty()
    val selected = notes.firstOrNull { it.id == selectedNoteId }

    Row(modifier.padding(8.dp)) {
        Column(Modifier.width(160.dp).fillMaxSize()) {
            OutlinedButton(
                onClick = {
                    val id = UUID.randomUUID().toString().take(8)
                    client.send(ClientMsg.GmAction.UpsertGmNote(GmNote(id, "Новая заметка", "")))
                    onSelectNote(id)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Заметка") }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(notes, key = { it.id }) { note ->
                    Text(
                        note.title.ifBlank { "(без названия)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (note.id == selectedNoteId) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onSelectNote(note.id) }
                            .padding(vertical = 6.dp),
                    )
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f).fillMaxSize()) {
            if (selected == null) {
                Text(
                    "Выберите заметку слева или создайте новую. Заметки комнат создаются из панели комнаты на доске.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                // Локальные черновики, чтобы снапшоты не дёргали курсор
                var title by remember(selected.id) { mutableStateOf(selected.title) }
                var text by remember(selected.id) { mutableStateOf(selected.text) }
                // Дебаунс: отправка после паузы в печатании
                LaunchedEffect(title, text) {
                    if (title != selected.title || text != selected.text) {
                        delay(400)
                        client.send(ClientMsg.GmAction.UpsertGmNote(GmNote(selected.id, title, text)))
                    }
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(80) },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        client.send(ClientMsg.GmAction.DeleteGmNote(selected.id))
                        onSelectNote(null)
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { Text("Описание комнаты, общие заметки по партии, заметки по игроку…") },
                )
            }
        }
    }
}
