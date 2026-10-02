package srooms.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import srooms.core.PlayerColor
import srooms.core.RoomColor

fun RoomColor.toCompose(): Color = when (this) {
    RoomColor.Default -> Color(0xFFC7C7C7)
    RoomColor.White -> Color(0xFFF5F5F5)
    RoomColor.Green -> Color(0xFF66BB6A)
    RoomColor.Yellow -> Color(0xFFFFEE58)
    RoomColor.Blue -> Color(0xFF42A5F5)
    RoomColor.Red -> Color(0xFFEF5350)
    RoomColor.Purple -> Color(0xFFAB47BC)
    RoomColor.Orange -> Color(0xFFFFA726)
}

fun PlayerColor.toCompose(): Color = when (this) {
    PlayerColor.Red -> Color(0xFFEF5350)
    PlayerColor.Coral -> Color(0xFFFF8A65)
    PlayerColor.Orange -> Color(0xFFFFA726)
    PlayerColor.Amber -> Color(0xFFFFCA28)
    PlayerColor.Yellow -> Color(0xFFFFEE58)
    PlayerColor.Lime -> Color(0xFFD4E157)
    PlayerColor.Green -> Color(0xFF66BB6A)
    PlayerColor.Emerald -> Color(0xFF26A69A)
    PlayerColor.Teal -> Color(0xFF00897B)
    PlayerColor.Cyan -> Color(0xFF4DD0E1)
    PlayerColor.Sky -> Color(0xFF81D4FA)
    PlayerColor.Blue -> Color(0xFF42A5F5)
    PlayerColor.Indigo -> Color(0xFF5C6BC0)
    PlayerColor.Violet -> Color(0xFF7E57C2)
    PlayerColor.Purple -> Color(0xFFAB47BC)
    PlayerColor.Magenta -> Color(0xFFEC407A)
    PlayerColor.Pink -> Color(0xFFF48FB1)
    PlayerColor.Rose -> Color(0xFFE57373)
    PlayerColor.Brown -> Color(0xFF8D6E63)
    PlayerColor.Silver -> Color(0xFFB0BEC5)
    PlayerColor.Gold -> Color(0xFFD4AF37)
    PlayerColor.White -> Color(0xFFF5F5F5)
    PlayerColor.Gray -> Color(0xFF78909C)
    PlayerColor.Black -> Color(0xFF212121)
}

/** Цвет текста поверх цвета комнаты. */
fun RoomColor.onColor(): Color = when (this) {
    RoomColor.Blue, RoomColor.Red, RoomColor.Purple -> Color.White
    else -> Color(0xFF212121)
}

@Composable
fun SRoomsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
