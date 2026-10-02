package srooms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import srooms.core.BOARD_SIZE
import srooms.core.GmNote
import srooms.core.Pos
import srooms.core.RoomColor
import srooms.protocol.ClientMsg
import srooms.protocol.GameView
import srooms.protocol.RoomView
import kotlinx.coroutines.delay
import java.util.UUID

private val CELL = 76.dp
private val ARROW = 28.dp

@Composable
fun BoardView(
    view: GameView,
    selectedRoom: Int?,
    pendingSwap: Int?,
    onRoomClick: (Int) -> Unit,
    onShiftRow: (row: Int, dir: Int) -> Unit,
    onShiftCol: (col: Int, dir: Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // стрелки столбцов вверх
        if (view.isGm) {
            Row {
                Spacer(Modifier.width(ARROW))
                repeat(BOARD_SIZE) { col ->
                    Box(Modifier.width(CELL), contentAlignment = Alignment.Center) {
                        ShiftButton("↑") { onShiftCol(col, -1) }
                    }
                }
                Spacer(Modifier.width(ARROW))
            }
        }
        repeat(BOARD_SIZE) { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (view.isGm) {
                    Box(Modifier.width(ARROW), contentAlignment = Alignment.Center) {
                        ShiftButton("←") { onShiftRow(row, -1) }
                    }
                }
                repeat(BOARD_SIZE) { col ->
                    val index = row * BOARD_SIZE + col
                    RoomCell(
                        room = view.rooms[index],
                        hasToken = view.tokenPos.index() == index,
                        selected = selectedRoom == index,
                        swapArmed = pendingSwap == index,
                        isGm = view.isGm,
                        onClick = { onRoomClick(index) },
                    )
                }
                if (view.isGm) {
                    Box(Modifier.width(ARROW), contentAlignment = Alignment.Center) {
                        ShiftButton("→") { onShiftRow(row, 1) }
                    }
                }
            }
        }
        if (view.isGm) {
            Row {
                Spacer(Modifier.width(ARROW))
                repeat(BOARD_SIZE) { col ->
                    Box(Modifier.width(CELL), contentAlignment = Alignment.Center) {
                        ShiftButton("↓") { onShiftCol(col, 1) }
                    }
                }
                Spacer(Modifier.width(ARROW))
            }
        }
        if (pendingSwap != null && view.isGm) {
            Text(
                "Режим обмена: кликните вторую комнату",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ShiftButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RoomCell(
    room: RoomView,
    hasToken: Boolean,
    selected: Boolean,
    swapArmed: Boolean,
    isGm: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = when {
        swapArmed -> MaterialTheme.colorScheme.primary
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.25f)
    }
    Box(
        Modifier.padding(2.dp).size(CELL)
            .clip(RoundedCornerShape(6.dp))
            .background(room.color.toCompose())
            .border(if (selected || swapArmed) 3.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(2.dp)) {
            Text(
                room.label,
                color = room.color.onColor(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (hasToken) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp)
                    .clip(CircleShape).background(Color.White)
                    .border(1.dp, Color.Black, CircleShape)
            )
        }
        if (isGm && room.revealed) {
            Text(
                "👁",
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (isGm && room.hasGmNote) {
            Text(
                "📝",
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Панель выбранной комнаты — только для ГМа. */
@Composable
fun RoomPanel(
    view: GameView,
    index: Int,
    pendingSwap: Int?,
    onAction: (ClientMsg) -> Unit,
    onSwapToggle: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val room = view.rooms.getOrNull(index) ?: return
    val pos = Pos.of(index)
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Комната (${pos.x + 1};${pos.y + 1})", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))

            var label by remember(room.id) { mutableStateOf(room.label) }
            // отправляем подпись после паузы в печатании, а не на каждый символ
            LaunchedEffect(label) {
                if (label != room.label) {
                    delay(400)
                    onAction(ClientMsg.GmAction.SetRoomLabel(index, label))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(24) },
                    label = { Text("Подпись") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))

            Text("Цвет:", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoomColor.entries.forEach { color ->
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(color.toCompose())
                            .border(
                                width = if (room.color == color) 3.dp else 1.dp,
                                color = if (room.color == color) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape,
                            )
                            .clickable { onAction(ClientMsg.GmAction.SetRoomColor(index, color)) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = room.revealed,
                    onCheckedChange = { onAction(ClientMsg.GmAction.SetRoomRevealed(index, it)) },
                )
                Spacer(Modifier.width(8.dp))
                Text("Цвет виден игрокам")
            }
            Spacer(Modifier.height(6.dp))

            Row {
                TextButton(onClick = { onAction(ClientMsg.GmAction.MoveToken(pos)) }) { Text("Фишка сюда") }
                TextButton(onClick = onSwapToggle) {
                    Text(if (pendingSwap == index) "Отменить обмен" else "Обменять с…")
                }
                RoomNoteButton(view, index, onAction, onOpenNote)
            }
        }
    }
}

@Composable
private fun RoomNoteButton(
    view: GameView,
    index: Int,
    onAction: (ClientMsg) -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val room = view.rooms.getOrNull(index) ?: return
    val noteId = room.gmNoteId
    if (noteId != null) {
        TextButton(onClick = { onOpenNote(noteId) }) { Text("Заметка комнаты") }
    } else {
        TextButton(onClick = {
            val newId = UUID.randomUUID().toString().take(8)
            val title = room.label.ifBlank { "Комната (${Pos.of(index).x + 1};${Pos.of(index).y + 1})" }
            onAction(ClientMsg.GmAction.UpsertGmNote(GmNote(newId, title, "")))
            onAction(ClientMsg.GmAction.SetRoomNote(index, newId))
            onOpenNote(newId)
        }) { Text("+ Заметка") }
    }
}
