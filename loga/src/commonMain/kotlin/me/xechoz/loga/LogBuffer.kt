package me.xechoz.loga

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

/**
 * The mmap-backed linear log buffer.
 *
 * Log lines are appended sequentially at `dataStart + logLen`. When the buffer
 * cannot fit a line it is flushed first; if a single line is larger than the
 * whole buffer it is truncated and a warning is emitted. Flushing copies the
 * valid region into a self-contained [FlushBuffer] and clears the buffer.
 *
 * The buffer file layout is `[header][data...]`, total size
 * `capacity + headerSize(logPathLen)`.
 */
internal class LogBuffer(
    private val bufferPath: String,
    private val capacity: Int,
    private val fileManager: LogFileManager,
    private val asyncFlush: AsyncFlush,
    private val clock: Clock = Clock.System,
) {
    private val lock = SynchronizedObject()
    private var mapped: MappedBuffer? = null
    private var logLen: Long = 0
    private var dataStart: Long = 0
    private var logPath: String = fileManager.currentFilePath()

    fun init() {
        val existingSize = if (FileSystem.exists(bufferPath)) FileSystem.size(bufferPath) else 0L

        // Recover the dirty tail left by a previous process BEFORE rebuilding
        // the mapping, otherwise the data would be lost.
        if (existingSize > 0) {
            recoverDirtyTail(existingSize)
        }

        val pathLen = logPath.encodeToByteArray().size
        val totalSize = capacity.toLong() + LogBufferHeader.headerSize(pathLen)
        val buffer = openMappedBuffer(bufferPath, totalSize)
        mapped = buffer
        dataStart = LogBufferHeader.headerSize(pathLen).toLong()
        logLen = 0
        LogBufferHeader.writeHeader(buffer, 0, logPath)
    }

    private fun recoverDirtyTail(existingSize: Long) {
        val buffer = openMappedBuffer(bufferPath, existingSize)
        try {
            if (!LogBufferHeader.isAvailable(readFirstByte(buffer))) return
            val dirtyLen = LogBufferHeader.readLogLen(buffer)
            val dirtyPath = LogBufferHeader.readLogPath(buffer)
            if (dirtyLen <= 0 || dirtyPath.isEmpty()) return
            val start = LogBufferHeader.headerSize(dirtyPath.encodeToByteArray().size).toLong()
            val available = existingSize - start
            val length = minOf(dirtyLen, available).toInt()
            if (length <= 0) return
            val data = ByteArray(length)
            buffer.get(start, data, 0, length)
            asyncFlush.submit(FlushBuffer(dirtyPath, data, length))
        } finally {
            buffer.close()
        }
    }

    private fun readFirstByte(buffer: MappedBuffer): ByteArray {
        val head = ByteArray(1)
        buffer.get(0, head, 0, 1)
        return head
    }

    fun append(text: String) {
        val bytes = text.encodeToByteArray()
        synchronized(lock) {
            appendLocked(bytes)
        }
    }

    private fun appendLocked(bytes: ByteArray) {
        val buffer = mapped ?: return

        val rotated = fileManager.rotateIfNeeded(clock.now().toEpochMilliseconds())
        if (rotated != null) {
            flushLocked()
            logPath = rotated
            LogBufferHeader.writeHeader(buffer, 0, logPath)
        }

        if (bytes.size > capacity) {
            consoleLog(Level.WARN, TAG, "log line (${bytes.size}B) exceeds buffer ($capacity B), truncated")
            val truncated = bytes.copyOf(capacity)
            writeLocked(buffer, truncated)
            flushLocked()
            return
        }

        if (logLen + bytes.size > capacity) {
            flushLocked()
        }
        writeLocked(buffer, bytes)
    }

    private fun writeLocked(buffer: MappedBuffer, bytes: ByteArray) {
        buffer.put(dataStart + logLen, bytes, 0, bytes.size)
        logLen += bytes.size
        LogBufferHeader.writeHeader(buffer, logLen, logPath)
    }

    fun flush() {
        synchronized(lock) {
            flushLocked()
        }
        asyncFlush.await()
    }

    private fun flushLocked() {
        val buffer = mapped ?: return
        if (logLen <= 0) return
        val length = logLen.toInt()
        val data = ByteArray(length)
        buffer.get(dataStart, data, 0, length)
        asyncFlush.submit(FlushBuffer(logPath, data, length))
        logLen = 0
        LogBufferHeader.writeHeader(buffer, 0, logPath)
    }

    fun release() {
        synchronized(lock) {
            flushLocked()
            mapped?.close()
            mapped = null
        }
        asyncFlush.shutdown()
    }

    companion object {
        private const val TAG = "loga"
    }
}
