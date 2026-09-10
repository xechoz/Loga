package me.xechoz.loga.demo

import androidx.compose.runtime.mutableStateListOf
import me.xechoz.loga.appender.Appender

class InMemoryAppender(private val maxLines: Int = 200) : Appender {
    val lines = mutableStateListOf<String>()

    override fun append(level: Int, tag: String, line: String) {
        lines.add(line.trimEnd('\n'))
        if (lines.size > maxLines) {
            lines.removeAt(0)
        }
    }

    override fun flush() = Unit

    override fun release() = Unit
}
