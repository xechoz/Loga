package me.xechoz.loga

import me.xechoz.loga.appender.Appender
import me.xechoz.loga.appender.ConsoleAppender
import me.xechoz.loga.formatter.DefaultFormatter
import me.xechoz.loga.formatter.Formatter

data class LogConfig(
    val logDirectory: String? = null,
    val bufferSize: Int = 400 * 1024,
    val level: Int = Level.DEBUG,
    val formatter: Formatter = DefaultFormatter,
    val retentionDays: Int = 7,
    /**
     * Extra appenders appended after the built-in file appender.
     * File logging is always enabled; pass [ConsoleAppender] to also print to the platform console.
     */
    val appenders: List<Appender> = listOf(ConsoleAppender()),
    /**
     * When enabled, [Loga.init] installs a platform uncaught-exception handler that logs the
     * crash at [Level.ERROR] and flushes before the process dies. [Loga.release] restores the
     * previously installed handler. Unsupported on iOS, where it is a no-op.
     */
    val logUncaughtExceptions: Boolean = true,
)
