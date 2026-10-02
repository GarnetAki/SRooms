package srooms.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import srooms.client.GameClient
import srooms.protocol.ClientMsg
import srooms.protocol.GameView

@Composable
fun GameScreen(view: GameView, client: GameClient, onLeave: () -> Unit) {
    val isHost by client.isHost.collectAsState()
    var selectedRoom by remember { mutableStateOf<Int?>(null) }
    var pendingSwap by remember { mutableStateOf<Int?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val tabs = remember(view.isGm) {
        buildList {
            add("Заметки")
            add("Сообщения")
            add(if (view.isGm) "Персонажи" else "Персонаж")
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // --- Верхняя панель ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SRooms", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Text(
                view.you.name,
                color = view.you.color?.toCompose() ?: MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            if (view.isGm) {
                Spacer(Modifier.width(8.dp))
                Text("(ГМ)", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            if (view.isGm) {
                OutlinedButton(onClick = { showSaveDialog = true }) { Text("Сохранить") }
                Spacer(Modifier.width(8.dp))
            }
            if (isHost) {
                OutlinedButton(onClick = { client.send(ClientMsg.ReturnToLobby) }) { Text("В лобби") }
                Spacer(Modifier.width(8.dp))
            }
            OutlinedButton(onClick = onLeave) { Text("Выйти") }
        }
        Spacer(Modifier.height(8.dp))

        // Колонки с минимальной шириной: при сужении окна — горизонтальный скролл,
        // при избытке места — делим пропорционально. Левая колонка — с вертикальным скроллом.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val boardW = maxOf(500.dp, maxWidth * 0.42f)
            val midW = maxOf(360.dp, maxWidth * 0.31f)
            val logW = maxOf(280.dp, maxWidth * 0.27f)

            Row(Modifier.horizontalScroll(rememberScrollState())) {
                // --- Доска + панель комнаты ---
                Column(Modifier.width(boardW).verticalScroll(rememberScrollState())) {
                    BoardView(
                        view = view,
                        selectedRoom = selectedRoom,
                        pendingSwap = pendingSwap,
                        onRoomClick = { index ->
                            val swap = pendingSwap
                            if (view.isGm && swap != null && swap != index) {
                                client.send(ClientMsg.GmAction.SwapRooms(swap, index))
                                pendingSwap = null
                                selectedRoom = index
                            } else {
                                selectedRoom = if (selectedRoom == index) null else index
                            }
                        },
                        onShiftRow = { row, dir -> client.send(ClientMsg.GmAction.ShiftRow(row, dir)) },
                        onShiftCol = { col, dir -> client.send(ClientMsg.GmAction.ShiftCol(col, dir)) },
                    )
                    if (view.isGm) {
                        selectedRoom?.let { index ->
                            RoomPanel(
                                view = view,
                                index = index,
                                pendingSwap = pendingSwap,
                                onAction = { client.send(it) },
                                onSwapToggle = { pendingSwap = if (pendingSwap == null) index else null },
                                onOpenNote = { noteId ->
                                    selectedNoteId = noteId
                                    selectedTab = 0
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // --- Средняя колонка: вкладки ---
                Column(Modifier.width(midW).fillMaxHeight()) {
                    TabRow(selectedTabIndex = selectedTab.coerceIn(0, tabs.size - 1)) {
                        tabs.forEachIndexed { i, title ->
                            Tab(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                text = { Text(title) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (tabs.getOrNull(selectedTab)) {
                        "Заметки" -> NotesPanel(
                            view = view,
                            client = client,
                            selectedNoteId = selectedNoteId,
                            onSelectNote = { selectedNoteId = it },
                            modifier = Modifier.fillMaxSize(),
                        )

                        "Сообщения" -> MessagesPanel(view = view, client = client, modifier = Modifier.fillMaxSize())
                        "Персонажи", "Персонаж" -> CharactersPanel(view = view, client = client, modifier = Modifier.fillMaxSize())
                    }
                }

                Spacer(Modifier.width(12.dp))

                // --- Правая колонка: лог всегда на виду ---
                Column(Modifier.width(logW).fillMaxHeight()) {
                    Text("Лог", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
                    LogsPanel(view = view, client = client, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    if (showSaveDialog) {
        var saveName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Сохранить сессию") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it.take(64) },
                    label = { Text("Название сейва") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = saveName.isNotBlank(),
                    onClick = {
                        client.send(ClientMsg.SaveSession(saveName.trim()))
                        showSaveDialog = false
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Отмена") }
            },
        )
    }
}
