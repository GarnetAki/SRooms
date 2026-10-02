package srooms.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import srooms.client.GameClient
import srooms.core.CardType
import srooms.core.CharCard
import srooms.core.CharacterSheet
import srooms.core.MAX_CARD_LEVEL
import srooms.core.Stat
import srooms.core.stat
import srooms.protocol.ClientMsg
import srooms.protocol.GameView
import java.util.UUID

@Composable
fun CharactersPanel(view: GameView, client: GameClient, modifier: Modifier = Modifier) {
    // У ГМа своего персонажа нет — он выбирает лист одного из игроков
    val owners = remember(view.players, view.gmId) { view.players.filter { it.id != view.gmId } }
    var selectedName by remember { mutableStateOf<String?>(null) }
    val sheetOwner = if (view.isGm) {
        selectedName?.takeIf { name -> owners.any { it.name == name } } ?: owners.firstOrNull()?.name
    } else {
        view.you.name
    }
    val sheet = sheetOwner?.let { view.sheets[it] } ?: CharacterSheet()
    var editingCard by remember { mutableStateOf<CharCard?>(null) }
    val listState = rememberLazyListState()
    var draggedCardId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    if (sheetOwner == null) {
        Column(modifier.padding(16.dp)) {
            Text(
                "Пока нет игроков — листы появятся, когда кто-то зайдёт в лобби.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier.padding(8.dp)) {
        if (view.isGm) {
            Text("Лист:", style = MaterialTheme.typography.labelMedium)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow {
                owners.forEach { player ->
                    FilterChip(
                        selected = player.name == sheetOwner,
                        onClick = { selectedName = player.name },
                        label = { Text(player.name) },
                        modifier = Modifier.padding(end = 6.dp, bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // --- Статы + вдохновения ---
        val canEdit = view.isGm || sheetOwner == view.you.name
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat.entries.forEach { stat ->
                StatBox(
                    name = stat.displayName,
                    value = sheet.stat(stat),
                    editable = canEdit,
                    onChange = { newValue ->
                        client.send(
                            ClientMsg.UpdateSheet(
                                playerName = sheetOwner,
                                stats = sheet.stats + (stat to newValue),
                                inspiration = sheet.inspiration,
                            )
                        )
                    },
                )
            }
            StatBox(
                name = "Вдохновения",
                value = sheet.inspiration,
                editable = canEdit,
                accent = true,
                onChange = { newValue ->
                    client.send(
                        ClientMsg.UpdateSheet(sheetOwner, sheet.stats, newValue)
                    )
                },
            )
        }
        Spacer(Modifier.height(8.dp))

        // --- Карточки ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Карточки", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            if (canEdit) {
                OutlinedButton(onClick = {
                    editingCard = CharCard(id = UUID.randomUUID().toString().take(8), type = CardType.Skill)
                }) { Text("+ Навык") }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = {
                    editingCard = CharCard(id = UUID.randomUUID().toString().take(8), type = CardType.Item)
                }) { Text("+ Предмет") }
            }
        }
        Spacer(Modifier.height(4.dp))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(sheet.cards, key = { _, c -> c.id }) { index, card ->
                val isDragging = draggedCardId == card.id
                Box(
                    Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        .then(
                            if (sheetOwner == view.you.name) {
                                Modifier.pointerInput(card.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedCardId = card.id
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                        },
                                        onDragEnd = {
                                            computeDropIndex(listState, card.id, dragOffset)?.let { target ->
                                                if (target != index) client.send(ClientMsg.MoveCard(card.id, target))
                                            }
                                            draggedCardId = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggedCardId = null
                                            dragOffset = 0f
                                        },
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    CardView(
                        card = card,
                        elevated = isDragging,
                        canReorder = sheetOwner == view.you.name,
                        canMoveUp = index > 0,
                        canMoveDown = index < sheet.cards.size - 1,
                        onMoveUp = { client.send(ClientMsg.MoveCard(card.id, index - 1)) },
                        onMoveDown = { client.send(ClientMsg.MoveCard(card.id, index + 1)) },
                        canEdit = canEdit,
                        onEdit = { editingCard = card },
                        onDelete = { client.send(ClientMsg.DeleteCard(sheetOwner, card.id)) },
                    )
                }
            }
            if (sheet.cards.isEmpty()) {
                item {
                    Text(
                        "Карточек пока нет — создайте навык или предмет.",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // --- Диалог создания/редактирования карточки (только ГМ) ---
    editingCard?.let { card ->
        CardEditDialog(
            initial = card,
            onDismiss = { editingCard = null },
            onSave = { updated ->
                client.send(ClientMsg.UpsertCard(sheetOwner, updated))
                editingCard = null
            },
        )
    }
}

@Composable
private fun StatBox(
    name: String,
    value: Int,
    editable: Boolean,
    accent: Boolean = false,
    onChange: (Int) -> Unit,
) {
    Card(Modifier.width(104.dp).padding(vertical = 3.dp)) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editable) {
                    IconButton(onClick = { onChange(value - 1) }, modifier = Modifier.width(24.dp)) {
                        Text("−")
                    }
                }
                Text(
                    "$value",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (editable) {
                    IconButton(onClick = { onChange(value + 1) }, modifier = Modifier.width(24.dp)) {
                        Text("+")
                    }
                }
            }
        }
    }
}

@Composable
private fun CardView(
    card: CharCard,
    elevated: Boolean = false,
    canReorder: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).shadow(if (elevated) 8.dp else 0.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.type.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (card.type == CardType.Skill) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "●".repeat(card.level) + "○".repeat(MAX_CARD_LEVEL - card.level),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Text(
                    card.title.ifBlank { "(без названия)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (card.description.isNotBlank()) {
                    Text(card.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (canReorder) {
                Column {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.width(28.dp)) {
                        Text("↑")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.width(28.dp)) {
                        Text("↓")
                    }
                }
            }
            if (canEdit) {
                Column {
                    TextButton(onClick = onEdit) { Text("✎") }
                    TextButton(onClick = onDelete) { Text("✕", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/** Позиция соседней карточки, чей центр ближе всего к центру перетаскиваемой. */
private fun computeDropIndex(listState: LazyListState, draggedId: String, offsetY: Float): Int? {
    val infos = listState.layoutInfo.visibleItemsInfo
    val dragged = infos.firstOrNull { it.key == draggedId } ?: return null
    val center = dragged.offset + offsetY + dragged.size / 2f
    val nearest = infos.filter { it.key != draggedId }
        .minByOrNull { kotlin.math.abs((it.offset + it.size / 2f) - center) }
        ?: return null
    return nearest.index
}

@Composable
private fun CardEditDialog(
    initial: CharCard,
    onDismiss: () -> Unit,
    onSave: (CharCard) -> Unit,
) {
    var type by remember { mutableStateOf(initial.type) }
    var title by remember { mutableStateOf(initial.title) }
    var level by remember { mutableStateOf(initial.level) }
    var description by remember { mutableStateOf(initial.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Карточка") },
        text = {
            Column {
                Row {
                    CardType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.displayName) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == CardType.Skill) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Уровень: ")
                        repeat(MAX_CARD_LEVEL) { i ->
                            TextButton(onClick = { level = i + 1 }) {
                                Text(if (i < level) "●" else "○")
                            }
                        }
                        TextButton(onClick = { level = 0 }) { Text("0") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(2000) },
                    label = { Text("Описание") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(initial.copy(type = type, title = title.trim(), level = level, description = description.trim()))
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
