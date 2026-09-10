package me.xechoz.loga.demo

import me.xechoz.loga.Loga

fun installCrashLogger() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        Loga.e("Crash", "Uncaught on ${thread.name}: ${throwable.stackTraceToString()}")
        Loga.flush()
    }
}
