package me.xechoz.loga.appender

import me.xechoz.loga.consoleLog

class ConsoleAppender : Appender {
    override fun append(level: Int, tag: String, line: String) {
        consoleLog(level, tag, line)
    }

    override fun flush() = Unit

    override fun release() = Unit
}
