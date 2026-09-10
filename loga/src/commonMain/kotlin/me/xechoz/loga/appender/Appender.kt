package me.xechoz.loga.appender

interface Appender {
    /**
     * @param line the fully formatted log line (including any trailing newline).
     */
    fun append(level: Int, tag: String, line: String)
    fun flush()
    fun release()
}
