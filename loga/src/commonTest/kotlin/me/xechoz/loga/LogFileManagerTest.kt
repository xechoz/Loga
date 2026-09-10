package me.xechoz.loga

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class LogFileManagerTest {
    private val utc = TimeZone.UTC

    private class MutableClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    @Test
    fun currentFileUsesDatePattern() {
        val clock = MutableClock(Instant.parse("2026-09-10T12:00:00Z"))
        val manager = LogFileManager("/logs", retentionDays = 7, clock = clock, timeZone = utc)
        assertEquals("/logs/2026_09_10.txt", manager.currentFilePath())
    }

    @Test
    fun noRotationWithinSameDay() {
        val clock = MutableClock(Instant.parse("2026-09-10T12:00:00Z"))
        val manager = LogFileManager("/logs", retentionDays = 7, clock = clock, timeZone = utc)
        assertNull(manager.rotateIfNeeded(clock.now().toEpochMilliseconds()))
    }

    @Test
    fun rotatesAfterDayBoundary() {
        val clock = MutableClock(Instant.parse("2026-09-10T23:59:00Z"))
        val manager = LogFileManager("/logs", retentionDays = 7, clock = clock, timeZone = utc)
        clock.instant = Instant.parse("2026-09-11T00:01:00Z")
        val rotated = manager.rotateIfNeeded(clock.now().toEpochMilliseconds())
        assertEquals("/logs/2026_09_11.txt", rotated)
    }

    @Test
    fun cleanupDeletesFilesOlderThanRetention() {
        val dir = "/tmp/loga/retention_test"
        FileSystem.mkdirs(dir)
        FileSystem.append("$dir/2026_09_01.txt", "old".encodeToByteArray(), 0, 3)
        FileSystem.append("$dir/2026_09_09.txt", "keep".encodeToByteArray(), 0, 4)
        FileSystem.append("$dir/not_a_date.txt", "x".encodeToByteArray(), 0, 1)

        val clock = MutableClock(Instant.parse("2026-09-10T12:00:00Z"))
        val manager = LogFileManager(dir, retentionDays = 7, clock = clock, timeZone = utc)
        manager.cleanup()

        assertTrue(!FileSystem.exists("$dir/2026_09_01.txt"))
        assertTrue(FileSystem.exists("$dir/2026_09_09.txt"))
        assertTrue(FileSystem.exists("$dir/not_a_date.txt"))
    }
}
