package srooms

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import srooms.ui.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "SRooms") {
        App()
    }
}
