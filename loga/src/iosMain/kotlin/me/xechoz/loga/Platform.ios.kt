package me.xechoz.loga

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun defaultLogDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    )
    val documents = paths.firstOrNull() as? String ?: "."
    return "$documents/logs"
}

actual fun consoleLog(level: Int, tag: String, line: String) {
    NSLog("%@", line.trimEnd('\n'))
}

actual fun installUncaughtExceptionHook(onUncaught: (message: String) -> Unit): () -> Unit = {}
