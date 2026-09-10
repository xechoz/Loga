package me.xechoz.loga

import me.xechoz.loga.appender.Appender
import me.xechoz.loga.formatter.Formatter
import kotlin.test.Test
import kotlin.test.assertEquals

class LogFormatterTest {
    private class CapturingAppender : Appender {
        val lines = mutableListOf<String>()
        override fun append(level: Int, tag: String, line: String) {
            lines.add(line)
        }

        override fun flush() = Unit
        override fun release() = Unit
    }

    @Test
    fun formatterIsAppliedExactlyOnce() {
        val appender = CapturingAppender()
        Loga.init(
            LogConfig(
                logDirectory = "/tmp/loga/formatter_test",
                formatter = Formatter { level, tag, msg -> "PREFIX|$tag|$msg" },
                appenders = listOf(appender),
            ),
        )
        Loga.i("Tag", "body")
        Loga.release()

        assertEquals(listOf("PREFIX|Tag|body"), appender.lines)
    }
}
