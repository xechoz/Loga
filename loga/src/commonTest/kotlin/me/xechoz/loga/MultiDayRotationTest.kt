package me.xechoz.loga

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class MultiDayRotationTest {
    private val dir = "/tmp/loga/multiday"
    private val utc = TimeZone.UTC

    private class MutableClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private fun clean() {
        for (name in FileSystem.list(dir)) {
            FileSystem.delete("$dir/$name")
        }
    }

    private fun readFile(name: String): String {
        val path = "$dir/$name"
        return FileSystem.read(path, 0, FileSystem.size(path).toInt()).decodeToString()
    }

    private fun txtFiles(): List<String> =
        FileSystem.list(dir).filter { it.endsWith(".txt") }.sorted()

    @Test
    fun contentLandsInCorrectDailyFiles() {
        FileSystem.mkdirs(dir)
        clean()

        val clock = MutableClock(Instant.parse("2026-09-10T12:00:00Z"))
        val asyncFlush = AsyncFlush()
        val buffer = LogBuffer(
            bufferPath = "$dir/.logCache",
            capacity = 4096,
            fileManager = LogFileManager(dir, retentionDays = 7, clock = clock, timeZone = utc),
            asyncFlush = asyncFlush,
            clock = clock,
        )
        buffer.init()

        buffer.append("I/Test: day1-a\n")
        buffer.append("I/Test: day1-b\n")

        clock.instant = Instant.parse("2026-09-11T12:00:00Z")
        buffer.append("I/Test: day2-a\n")

        buffer.flush()
        buffer.release()

        assertEquals(listOf("2026_09_10.txt", "2026_09_11.txt"), txtFiles())

        val day1 = readFile("2026_09_10.txt")
        assertTrue(day1.contains("day1-a"), "day1 was: $day1")
        assertTrue(day1.contains("day1-b"), "day1 was: $day1")
        assertTrue(!day1.contains("day2-a"), "day1 was: $day1")

        val day2 = readFile("2026_09_11.txt")
        assertTrue(day2.contains("day2-a"), "day2 was: $day2")
        assertTrue(!day2.contains("day1-a"), "day2 was: $day2")
    }

    @Test
    fun unflushedContentStaysInPreviousDayFile() {
        FileSystem.mkdirs(dir)
        clean()

        val clock = MutableClock(Instant.parse("2026-09-10T12:00:00Z"))
        val asyncFlush = AsyncFlush()
        val buffer = LogBuffer(
            bufferPath = "$dir/.logCache",
            capacity = 4096,
            fileManager = LogFileManager(dir, retentionDays = 7, clock = clock, timeZone = utc),
            asyncFlush = asyncFlush,
            clock = clock,
        )
        buffer.init()

        buffer.append("I/Test: before-midnight\n")

        clock.instant = Instant.parse("2026-09-11T00:01:00Z")
        buffer.append("I/Test: after-midnight\n")

        buffer.flush()
        buffer.release()

        assertEquals(listOf("2026_09_10.txt", "2026_09_11.txt"), txtFiles())

        val day1 = readFile("2026_09_10.txt")
        assertTrue(day1.contains("before-midnight"), "day1 was: $day1")
        assertTrue(!day1.contains("after-midnight"), "day1 was: $day1")

        val day2 = readFile("2026_09_11.txt")
        assertTrue(day2.contains("after-midnight"), "day2 was: $day2")
        assertTrue(!day2.contains("before-midnight"), "day2 was: $day2")
    }

    @Test
    fun retentionCleansAcrossMultipleDays() {
        FileSystem.mkdirs(dir)
        clean()

        for (day in 1..10) {
            val name = "2026_09_${day.toString().padStart(2, '0')}.txt"
            FileSystem.append("$dir/$name", "x".encodeToByteArray(), 0, 1)
        }

        val clock = MutableClock(Instant.parse("2026-09-10T12:00:00Z"))
        val asyncFlush = AsyncFlush()
        val buffer = LogBuffer(
            bufferPath = "$dir/.logCache",
            capacity = 4096,
            fileManager = LogFileManager(dir, retentionDays = 7, clock = clock, timeZone = utc),
            asyncFlush = asyncFlush,
            clock = clock,
        )
        buffer.init()

        clock.instant = Instant.parse("2026-09-12T12:00:00Z")
        buffer.append("I/Test: trigger-rotation\n")
        buffer.flush()
        buffer.release()

        for (day in 1..4) {
            val name = "2026_09_${day.toString().padStart(2, '0')}.txt"
            assertTrue(!FileSystem.exists("$dir/$name"), "$name should be deleted")
        }
        for (day in 5..10) {
            val name = "2026_09_${day.toString().padStart(2, '0')}.txt"
            assertTrue(FileSystem.exists("$dir/$name"), "$name should be kept")
        }
    }
}
