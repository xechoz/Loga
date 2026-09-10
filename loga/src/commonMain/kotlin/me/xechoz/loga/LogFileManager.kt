package me.xechoz.loga

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Handles daily log file naming, rotation boundaries and retention cleanup.
 *
 * File names use the `yyyy_MM_dd.txt` pattern. Rotation is driven by a cached
 * [nextSwitchMillis] boundary so the hot path only performs a single Long
 * comparison instead of formatting a date per line.
 */
internal class LogFileManager(
    private val directory: String,
    private val retentionDays: Int,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private var currentDate: LocalDate = today()
    private var nextSwitchMillis: Long = endOfDayMillis(currentDate)

    fun currentFilePath(): String = "$directory/${fileName(currentDate)}"

    /**
     * Returns the log file path to use, rotating to a new day if the boundary
     * has been crossed. Returns `null` when no rotation happened.
     */
    fun rotateIfNeeded(nowMillis: Long): String? {
        if (nowMillis < nextSwitchMillis) return null
        currentDate = today()
        nextSwitchMillis = endOfDayMillis(currentDate)
        cleanup()
        return currentFilePath()
    }

    fun cleanup() {
        if (retentionDays <= 0) return
        val cutoff = currentDate.minus(retentionDays, DateTimeUnit.DAY)
        for (name in FileSystem.list(directory)) {
            val date = parseDate(name) ?: continue
            if (date < cutoff) {
                FileSystem.delete("$directory/$name")
            }
        }
    }

    private fun today(): LocalDate =
        clock.now().toLocalDateTime(timeZone).date

    private fun endOfDayMillis(date: LocalDate): Long =
        date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()

    private fun fileName(date: LocalDate): String {
        val month = date.monthNumber.toString().padStart(2, '0')
        val day = date.dayOfMonth.toString().padStart(2, '0')
        return "${date.year}_${month}_${day}.txt"
    }

    private fun parseDate(name: String): LocalDate? {
        if (!name.endsWith(".txt")) return null
        val parts = name.removeSuffix(".txt").split("_")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        return try {
            LocalDate(year, month, day)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
