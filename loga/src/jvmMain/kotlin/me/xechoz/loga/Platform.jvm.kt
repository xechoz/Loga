package me.xechoz.loga

actual fun defaultLogDirectory(): String {
    val home = System.getProperty("user.home") ?: "."
    return "$home/logs"
}

actual fun consoleLog(level: Int, tag: String, line: String) {
    print(line)
}

actual fun installUncaughtExceptionHook(onUncaught: (String) -> Unit): () -> Unit {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        onUncaught("Uncaught on ${thread.name}: ${throwable.stackTraceToString()}")
        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            throwable.printStackTrace()
        }
    }
    return { Thread.setDefaultUncaughtExceptionHandler(previous) }
}
