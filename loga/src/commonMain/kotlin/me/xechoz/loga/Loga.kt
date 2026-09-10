package me.xechoz.loga

import me.xechoz.loga.appender.Appender
import me.xechoz.loga.appender.ConsoleAppender
import me.xechoz.loga.appender.FileAppender
import me.xechoz.loga.formatter.Formatter

object Loga {
    private var config: LogConfig? = null
    private var appenders: List<Appender> = emptyList()
    private var formatter: Formatter = LogConfig().formatter
    private var level: Int = Level.DEBUG
    private var uninstallCrashHook: (() -> Unit)? = null

    fun init(config: LogConfig) {
        release()

        val directory = config.logDirectory ?: defaultLogDirectory()
        FileSystem.mkdirs(directory)

        val fileManager = LogFileManager(directory, config.retentionDays)
        fileManager.cleanup()

        val asyncFlush = AsyncFlush()
        val buffer = LogBuffer(
            bufferPath = "$directory/.logCache",
            capacity = config.bufferSize,
            fileManager = fileManager,
            asyncFlush = asyncFlush,
        )
        buffer.init()

        this.config = config
        this.formatter = config.formatter
        this.level = config.level
        val extraAppenders = config.appenders
            ?: if (config.isDebug) listOf(ConsoleAppender()) else emptyList()
        this.appenders = listOf(FileAppender(buffer)) + extraAppenders

        if (config.logUncaughtExceptions) {
            uninstallCrashHook = installUncaughtExceptionHook { message ->
                e("Crash", message)
                flush()
            }
        }
    }

    fun v(tag: String, msg: String) = println(Level.VERBOSE, tag, msg)

    fun d(tag: String, msg: String) = println(Level.DEBUG, tag, msg)

    fun i(tag: String, msg: String) = println(Level.INFO, tag, msg)

    fun w(tag: String, msg: String) = println(Level.WARN, tag, msg)

    fun e(tag: String, msg: String) = println(Level.ERROR, tag, msg)

    fun println(level: Int, tag: String, msg: String) {
        if (level < this.level) return
        val line = formatter.format(level, tag, msg)
        for (appender in appenders) {
            appender.append(level, tag, line)
        }
    }

    fun flush() {
        for (appender in appenders) {
            appender.flush()
        }
    }

    fun release() {
        uninstallCrashHook?.invoke()
        uninstallCrashHook = null
        for (appender in appenders) {
            appender.release()
        }
        appenders = emptyList()
        config = null
    }
}
