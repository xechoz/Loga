package me.xechoz.loga.appender

import me.xechoz.loga.LogBuffer

internal class FileAppender(private val buffer: LogBuffer) : Appender {
    override fun append(level: Int, tag: String, line: String) {
        buffer.append(line)
    }

    override fun flush() {
        buffer.flush()
    }

    override fun release() {
        buffer.release()
    }
}
