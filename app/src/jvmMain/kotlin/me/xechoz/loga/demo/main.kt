package me.xechoz.loga.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import me.xechoz.loga.Loga
import me.xechoz.loga.LogConfig

fun main() {
    Loga.init(LogConfig())
    application {
        Window(
            onCloseRequest = {
                Loga.flush()
                exitApplication()
            },
            title = "loga demo",
        ) {
            App()
        }
    }
}
