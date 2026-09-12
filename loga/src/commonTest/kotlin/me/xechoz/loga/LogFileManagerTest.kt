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
    fun rotatesAcrossMultipleDays() {
        val clock = MutableClock(Instant.parse("2026-09-10T23:59:00Z"))
        val manager = LogFileManager("/logs", retentionDays = 7, clock = clock, timeZone = utc)

        clock.instant = Instant.parse("2026-09-11T00:01:00Z")
        assertEquals("/logs/2026_09_11.txt", manager.rotateIfNeeded(clock.now().toEpochMilliseconds()))

        clock.instant = Instant.parse("2026-09-11T12:00:00Z")
        assertNull(manager.rotateIfNeeded(clock.now().toEpochMilliseconds()))

        clock.instant = Instant.parse("2026-09-12T00:01:00Z")
        assertEquals("/logs/2026_09_12.txt", manager.rotateIfNeeded(clock.now().toEpochMilliseconds()))
    }

    @Test
    fun rotatesAcrossMonthBoundary() {
        val clock = MutableClock(Instant.parse("2026-09-30T23:59:00Z"))
        val manager = LogFileManager("/logs", retentionDays = 7, clock = clock, timeZone = utc)

        clock.instant = Instant.parse("2026-10-01T00:01:00Z")
        assertEquals("/logs/2026_10_01.txt", manager.rotateIfNeeded(clock.now().toEpochMilliseconds()))
    }

    @Test
    fun rotatesAcrossYearBoundary() {
        val clock = MutableClock(Instant.parse("2026-12-31T23:59:00Z"))
        val manager = LogFileManager("/logs", retentionDays = 7, clock = clock, timeZone = utc)

        clock.instant = Instant.parse("2027-01-01T00:01:00Z")
        assertEquals("/logs/2027_01_01.txt", manager.rotateIfNeeded(clock.now().toEpochMilliseconds()))
    }

    @Test
    fun rotationSkipsIntermediateDays() {
        val clock = MutableClock(Instant.parse("2026-09-10T12:00:00Z"))
        val manager = LogFileManager("/logs", retentionDays = 7, clock = clock, timeZone = utc)

        clock.instant = Instant.parse("2026-09-13T12:00:00Z")
        assertEquals("/logs/2026_09_13.txt", manager.rotateIfNeeded(clock.now().toEpochMilliseconds()))
    }

    @Test
    fun cleanupRunsOnEachRotationAcrossDays() {
        val dir = "/tmp/loga/retention_multi"
        FileSystem.mkdirs(dir)
        for (day in 1..10) {
            val name = "2026_09_${day.toString().padStart(2, '0')}.txt"
            FileSystem.append("$dir/$name", "x".encodeToByteArray(), 0, 1)
        }

        val clock = MutableClock(Instant.parse("2026-09-10T12:00:00Z"))
        val manager = LogFileManager(dir, retentionDays = 7, clock = clock, timeZone = utc)

        clock.instant = Instant.parse("2026-09-12T12:00:00Z")
        manager.rotateIfNeeded(clock.now().toEpochMilliseconds())

        for (day in 1..4) {
            val name = "2026_09_${day.toString().padStart(2, '0')}.txt"
            assertTrue(!FileSystem.exists("$dir/$name"), "$name should be deleted")
        }
        for (day in 5..10) {
            val name = "2026_09_${day.toString().padStart(2, '0')}.txt"
            assertTrue(FileSystem.exists("$dir/$name"), "$name should be kept")
        }
    }

    @Test
    fun usesLocalTimeZoneBoundary() {
        val shanghai = TimeZone.of("Asia/Shanghai")
        val clock = MutableClock(Instant.parse("2026-09-10T15:59:00Z"))
        val manager = LogFileManager("/logs", retentionDays = 7, clock = clock, timeZone = shanghai)
        assertEquals("/logs/2026_09_10.txt", manager.currentFilePath())

        clock.instant = Instant.parse("2026-09-10T16:00:00Z")
        assertEquals("/logs/2026_09_11.txt", manager.rotateIfNeeded(clock.now().toEpochMilliseconds()))
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
