package me.xechoz.loga

object Level {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
    const val DISABLE = Int.MAX_VALUE

    fun letter(level: Int): Char = when (level) {
        VERBOSE -> 'V'
        DEBUG -> 'D'
        INFO -> 'I'
        WARN -> 'W'
        ERROR -> 'E'
        DISABLE -> 'F' // for OFF
        else -> '?'
    }
}
