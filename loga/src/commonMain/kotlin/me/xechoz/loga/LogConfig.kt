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
     * Controls the default appenders when [appenders] is `null`. When `true` the platform
     * console is enabled in addition to the built-in file appender; when `false` only the
     * file appender is used. Callers should pass their build's debug flag (e.g. Android
     * `BuildConfig.DEBUG`) so release builds stay file-only.
     */
    val isDebug: Boolean = true,
    /**
     * Extra appenders appended after the built-in file appender.
     * File logging is always enabled. When `null`, defaults to `[ConsoleAppender]` if
     * [isDebug] is `true`, otherwise `emptyList()`. Pass an explicit list to override.
     */
    val appenders: List<Appender>? = null,
    /**
     * When enabled, [Loga.init] installs a platform uncaught-exception handler that logs the
     * crash at [Level.ERROR] and flushes before the process dies. [Loga.release] restores the
     * previously installed handler. Unsupported on iOS, where it is a no-op.
     */
    val logUncaughtExceptions: Boolean = true,
)
