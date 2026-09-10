package me.xechoz.loga

import android.content.Context
import android.util.Log as AndroidLog

private var appContext: Context? = null

actual fun defaultLogDirectory(): String {
    val context = appContext
        ?: error("Loga.init(context, config) must be called before using the default log directory")
    val dir = context.getExternalFilesDir("logs") ?: context.filesDir
    return "${dir.absolutePath}/logs"
}

actual fun consoleLog(level: Int, tag: String, line: String) {
    val priority = when (level) {
        Level.VERBOSE -> AndroidLog.VERBOSE
        Level.DEBUG -> AndroidLog.DEBUG
        Level.INFO -> AndroidLog.INFO
        Level.WARN -> AndroidLog.WARN
        Level.ERROR -> AndroidLog.ERROR
        else -> AndroidLog.DEBUG
    }
    AndroidLog.println(priority, tag, line.trimEnd('\n'))
}

fun Loga.init(context: Context, config: LogConfig = LogConfig()) {
    appContext = context.applicationContext
    init(config)
}
