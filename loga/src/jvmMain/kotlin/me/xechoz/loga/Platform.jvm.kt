package me.xechoz.loga

actual fun defaultLogDirectory(): String {
    val home = System.getProperty("user.home") ?: "."
    return "$home/logs"
}

actual fun consoleLog(level: Int, tag: String, line: String) {
    print(line)
}
