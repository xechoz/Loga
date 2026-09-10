package me.xechoz.loga

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogBufferHeaderTest {
    @Test
    fun headerSizeIncludesFixedAndPath() {
        assertEquals(13, LogBufferHeader.headerSize(0))
        assertEquals(13 + 10, LogBufferHeader.headerSize(10))
    }

    @Test
    fun writeAndReadRoundTrip() {
        val path = "/tmp/loga/2026_09_10.txt"
        val buffer = openMappedBuffer("/tmp/loga/header_test.bin", 1024)
        try {
            LogBufferHeader.writeHeader(buffer, 123L, path)
            assertTrue(LogBufferHeader.isAvailable(readFirst(buffer)))
            assertEquals(123L, LogBufferHeader.readLogLen(buffer))
            assertEquals(path, LogBufferHeader.readLogPath(buffer))
        } finally {
            buffer.close()
        }
    }

    @Test
    fun emptyBufferIsNotAvailable() {
        val buffer = openMappedBuffer("/tmp/loga/empty_test.bin", 64)
        try {
            assertFalse(LogBufferHeader.isAvailable(readFirst(buffer)))
        } finally {
            buffer.close()
        }
    }

    private fun readFirst(buffer: MappedBuffer): ByteArray {
        val head = ByteArray(1)
        buffer.get(0, head, 0, 1)
        return head
    }
}
