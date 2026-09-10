package me.xechoz.loga

/**
 * A fixed-size memory region backed by a file mapping.
 *
 * Implementations map a file into the process address space so that writes go
 * directly to memory (and are eventually written back by the OS). The mapping
 * survives a process kill: dirty pages remain in the kernel page cache and are
 * flushed to disk. It does NOT survive power loss unless [force] is called.
 */
expect class MappedBuffer {
    val size: Long

    fun put(position: Long, src: ByteArray, offset: Int, length: Int)

    fun get(position: Long, dst: ByteArray, offset: Int, length: Int)

    fun force()

    fun close()
}

expect fun openMappedBuffer(path: String, size: Long): MappedBuffer

expect fun defaultLogDirectory(): String

/**
 * Writes an already-formatted log line to the platform console without adding
 * any prefix of its own.
 */
expect fun consoleLog(level: Int, tag: String, line: String)

/**
 * Installs a process-wide uncaught-exception handler that invokes [onUncaught]
 * with a pre-formatted crash description before delegating to the previously
 * installed handler.
 *
 * Returns a function that uninstalls the hook and restores the previous handler.
 * On platforms without such a mechanism (iOS) this is a no-op.
 */
expect fun installUncaughtExceptionHook(onUncaught: (message: String) -> Unit): () -> Unit
