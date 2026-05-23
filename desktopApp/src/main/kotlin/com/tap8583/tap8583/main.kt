package com.tap8583.tap8583

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tap8583.tap8583.ui.AppViewModel
import com.tap8583.tap8583.ui.MainScreen

fun main() {
    // Create ViewModel outside the application block — it owns the engine + scope
    val viewModel = AppViewModel(port = AppEngine.DEFAULT_PORT)

    application {
        Window(
            onCloseRequest = {
                viewModel.stop()
                exitApplication()
            },
            title = "Tap8583  —  ISO 8583 Visualizer",
            state = rememberWindowState(width = 1440.dp, height = 900.dp),
        ) {
            MainScreen(viewModel = viewModel)
        }
    }
}