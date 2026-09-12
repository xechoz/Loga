package me.xechoz.loga

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class PeriodicFlushTest {
    private val dir = "/tmp/loga/periodic"

    private fun clean() {
        for (name in FileSystem.list(dir)) {
            FileSystem.delete("$dir/$name")
        }
    }

    @Test
    fun flushAsyncWritesWithoutBlocking() {
        FileSystem.mkdirs(dir)
        clean()

        val asyncFlush = AsyncFlush()
        val buffer = LogBuffer(
            bufferPath = "$dir/.logCache",
            capacity = 4096,
            fileManager = LogFileManager(dir, retentionDays = 7),
            asyncFlush = asyncFlush,
        )
        buffer.init()
        buffer.append("I/Test: periodic\n")
        buffer.flushAsync()
        asyncFlush.await()
        buffer.release()

        val file = FileSystem.list(dir).first { it.endsWith(".txt") }
        val content = FileSystem.read("$dir/$file", 0, FileSystem.size("$dir/$file").toInt()).decodeToString()
        assertTrue(content.contains("I/Test: periodic"), "content was: $content")
    }

    @Test
    fun timerFlushesWithoutExplicitFlush() = runBlocking {
        FileSystem.mkdirs(dir)
        clean()

        Loga.init(
            LogConfig(
                logDirectory = dir,
                bufferSize = 4096,
                retentionDays = 7,
                flushIntervalMillis = 50,
            ),
        )
        Loga.i("Test", "timer-flushed")

        val deadline = 2_000L
        var content = ""
        var waited = 0L
        while (waited < deadline) {
            val files = FileSystem.list(dir).filter { it.endsWith(".txt") }
            if (files.isNotEmpty()) {
                content = FileSystem.read("$dir/${files.first()}", 0, FileSystem.size("$dir/${files.first()}").toInt())
                    .decodeToString()
                if (content.contains("timer-flushed")) break
            }
            delay(20)
            waited += 20
        }
        Loga.release()

        assertTrue(content.contains("timer-flushed"), "content was: $content")
    }
}
