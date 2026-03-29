package com.dergruenkohl.newsillyimagedownloader

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main(args: Array<String>) = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "newsillyimagedownloader",
        state = rememberWindowState(height = 800.dp),
    ) {
        App()
    }
}
