package me.xechoz.loga

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogEndToEndTest {
    private val dir = "/tmp/loga/e2e"

    private fun clean() {
        for (name in FileSystem.list(dir)) {
            FileSystem.delete("$dir/$name")
        }
    }

    @Test
    fun writesAndFlushesToDailyFile() {
        FileSystem.mkdirs(dir)
        clean()

        Loga.init(LogConfig(logDirectory = dir, bufferSize = 4096, retentionDays = 7))
        Loga.i("Test", "hello")
        Loga.i("Test", "world")
        Loga.flush()
        Loga.release()

        val files = FileSystem.list(dir).filter { it.endsWith(".txt") }
        assertEquals(1, files.size)
        val content = FileSystem.read("$dir/${files.first()}", 0, FileSystem.size("$dir/${files.first()}").toInt())
            .decodeToString()
        assertTrue(content.contains("I/Test: hello"))
        assertTrue(content.contains("I/Test: world"))
    }

    @Test
    fun levelFilteringDropsLowerLevels() {
        FileSystem.mkdirs(dir)
        clean()

        Loga.init(LogConfig(logDirectory = dir, bufferSize = 4096, level = Level.WARN))
        Loga.d("Test", "debug-should-be-dropped")
        Loga.w("Test", "warn-should-appear")
        Loga.flush()
        Loga.release()

        val file = FileSystem.list(dir).first { it.endsWith(".txt") }
        val content = FileSystem.read("$dir/$file", 0, FileSystem.size("$dir/$file").toInt()).decodeToString()
        assertTrue(!content.contains("debug-should-be-dropped"))
        assertTrue(content.contains("warn-should-appear"))
    }

    @Test
    fun oversizedLineIsTruncatedNotLost() {
        FileSystem.mkdirs(dir)
        clean()

        Loga.init(LogConfig(logDirectory = dir, bufferSize = 128, retentionDays = 7))
        Loga.i("Test", "x".repeat(500))
        Loga.flush()
        Loga.release()

        val file = FileSystem.list(dir).first { it.endsWith(".txt") }
        assertTrue(FileSystem.size("$dir/$file") > 0)
    }
}
