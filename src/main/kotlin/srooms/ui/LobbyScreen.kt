package srooms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import srooms.client.GameClient
import srooms.core.PlayerColor
import srooms.protocol.ClientMsg
import srooms.protocol.ServerMsg

@Composable
fun LobbyScreen(view: ServerMsg.LobbyState, client: GameClient, onLeave: () -> Unit) {
    val myId by client.myId.collectAsState()
    val isHost = myId != null && myId == view.hostId
    var selectedSave by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Лобби", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Text(
                "порт ${view.port} — продиктуйте его игрокам вместе с вашим IP из Radmin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onLeave) { Text("Покинуть лобби") }
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxSize()) {
            // --- Игроки ---
            Card(Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Игроки (${view.players.size})", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(view.players) { player ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Box(
                                    Modifier.size(18.dp).clip(CircleShape)
                                        .background(player.color?.toCompose() ?: Color.DarkGray)
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(player.name, style = MaterialTheme.typography.bodyLarge)
                                if (player.id == view.hostId) {
                                    Spacer(Modifier.width(6.dp)); Badge("хост")
                                }
                                if (player.id == view.gmId) {
                                    Spacer(Modifier.width(6.dp)); Badge("ГМ")
                                }
                                if (player.id == myId) {
                                    Spacer(Modifier.width(6.dp)); Badge("вы")
                                }
                                Spacer(Modifier.weight(1f))
                                if (isHost && player.id != view.gmId) {
                                    TextButton(onClick = { client.send(ClientMsg.AssignGm(player.id)) }) {
                                        Text("Назначить ГМом")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Ваш цвет:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val me = view.players.firstOrNull { it.id == myId }
                        val taken = view.players.filter { it.id != myId }.mapNotNull { it.color }.toSet()
                        PlayerColor.entries.forEach { color ->
                            val enabled = color !in taken
                            Box(
                                Modifier.padding(vertical = 3.dp).size(28.dp).clip(CircleShape)
                                    .background(color.toCompose().copy(alpha = if (enabled) 1f else 0.25f))
                                    .border(
                                        width = if (me?.color == color) 3.dp else 1.dp,
                                        color = if (me?.color == color) Color.White else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape,
                                    )
                                    .clickable(enabled = enabled) { client.send(ClientMsg.SetColor(color)) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // --- Старт сессии ---
            Card(Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Сессия", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    if (isHost) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedSave == null, onClick = { selectedSave = null })
                            Text("Новая сессия")
                        }
                        Text("Сохранённые сессии:", style = MaterialTheme.typography.titleSmall)
                        LazyColumn(Modifier.weight(1f)) {
                            items(view.saves) { save ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { selectedSave = save },
                                ) {
                                    RadioButton(selected = selectedSave == save, onClick = { selectedSave = save })
                                    Text(save)
                                }
                            }
                            if (view.saves.isEmpty()) {
                                item {
                                    Text(
                                        "Пока нет сейвов. Можно стартовать в соло, подготовить доску и сохранить — сейв появится здесь.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { client.send(ClientMsg.StartSession(selectedSave)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Начать сессию") }
                        Text(
                            "Можно начать и в одиночку — для подготовки доски и заметок.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("Хост выбирает сессию и запускает игру. Ожидайте…", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "ГМ: ${view.players.firstOrNull { it.id == view.gmId }?.name ?: "?"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
