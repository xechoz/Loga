package me.xechoz.loga.demo

import android.content.Context

private var appContext: Context? = null

internal fun setDemoContext(context: Context) {
    appContext = context.applicationContext
}

actual fun demoLogDirectory(): String {
    val context = appContext ?: error("setDemoContext must be called first")
    return "${context.filesDir.absolutePath}/loga-demo"
}
