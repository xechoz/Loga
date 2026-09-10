package me.xechoz.loga

import kotlin.test.Test
import kotlin.test.assertTrue

class DirtyTailRecoveryTest {
    private val dir = "/tmp/loga/recovery"

    private fun clean() {
        for (name in FileSystem.list(dir)) {
            FileSystem.delete("$dir/$name")
        }
    }

    @Test
    fun recoversDirtyTailLeftByPreviousProcess() {
        FileSystem.mkdirs(dir)
        clean()

        // Simulate a previous process that wrote into the mmap buffer but was
        // killed before flushing: the buffer file holds a valid header with a
        // non-zero logLen and the data still in the mapping.
        val bufferPath = "$dir/.logCache"
        val logPath = "$dir/2026_09_10.txt"
        val payload = "I/Test: survived-kill\n".encodeToByteArray()
        val pathLen = logPath.encodeToByteArray().size
        val totalSize = 4096L + LogBufferHeader.headerSize(pathLen)

        val buffer = openMappedBuffer(bufferPath, totalSize)
        try {
            LogBufferHeader.writeHeader(buffer, payload.size.toLong(), logPath)
            buffer.put(LogBufferHeader.headerSize(pathLen).toLong(), payload, 0, payload.size)
        } finally {
            buffer.close()
        }

        // A new process starts and must recover the dirty tail.
        Loga.init(LogConfig(logDirectory = dir, bufferSize = 4096, retentionDays = 7))
        Loga.flush()
        Loga.release()

        assertTrue(FileSystem.exists(logPath))
        val content = FileSystem.read(logPath, 0, FileSystem.size(logPath).toInt()).decodeToString()
        assertTrue(content.contains("survived-kill"), "recovered content was: $content")
    }
}
