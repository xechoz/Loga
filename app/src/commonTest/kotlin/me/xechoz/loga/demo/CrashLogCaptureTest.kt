package me.xechoz.loga.demo

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import me.xechoz.loga.FileSystem
import me.xechoz.loga.Loga
import me.xechoz.loga.LogConfig

class CrashLogCaptureTest {
    private val dir = "/tmp/loga/demo"

    private fun clean() {
        FileSystem.mkdirs(dir)
        for (name in FileSystem.list(dir)) {
            FileSystem.delete("$dir/$name")
        }
    }

    private fun waitUntilCrashCaptured(memory: InMemoryAppender) {
        val mark = TimeSource.Monotonic.markNow()
        while (memory.lines.none { it.contains("demo crash") } &&
            mark.elapsedNow().inWholeMilliseconds < 5_000
        ) {
            // busy-wait for the background crash thread to run the hook
        }
    }

    @Test
    fun uncaughtExceptionCapturedByInMemoryAppender() {
        clean()
        val memory = InMemoryAppender()
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Loga.init(LogConfig(logDirectory = dir, appenders = listOf(memory)))
        try {
            crashInBackgroundThread()
            waitUntilCrashCaptured(memory)
        } finally {
            Loga.release()
        }

        assertEquals(previous, Thread.getDefaultUncaughtExceptionHandler())
        assertTrue(memory.lines.any { it.contains("demo-crash") && it.contains("demo crash") })
    }

    @Test
    fun crashIsFlushedToLogFile() {
        clean()
        val memory = InMemoryAppender()

        Loga.init(LogConfig(logDirectory = dir, appenders = listOf(memory)))
        try {
            crashInBackgroundThread()
            waitUntilCrashCaptured(memory)
        } finally {
            Loga.release()
        }

        val file = FileSystem.list(dir).first { it.endsWith(".txt") }
        val content = FileSystem.read("$dir/$file", 0, FileSystem.size("$dir/$file").toInt()).decodeToString()
        assertTrue(content.contains("demo crash"))
    }

    @Test
    fun mainThreadCrashIsCaptured() {
        clean()
        val memory = InMemoryAppender()
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Loga.init(LogConfig(logDirectory = dir, appenders = listOf(memory)))
        try {
            thread(name = "main") { crashInMainThread() }.join()
        } finally {
            Loga.release()
        }

        assertEquals(previous, Thread.getDefaultUncaughtExceptionHandler())
        assertTrue(memory.lines.any { it.contains("main") && it.contains("demo crash (main)") })
    }

    @Test
    fun inMemoryAppenderCapsLines() {
        val memory = InMemoryAppender(maxLines = 200)
        repeat(210) { memory.append(0, "Test", "line #$it\n") }

        assertEquals(200, memory.lines.size)
        assertEquals("line #10", memory.lines.first())
        assertEquals("line #209", memory.lines.last())
    }
}
