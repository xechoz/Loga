package me.xechoz.loga

/**
 * Persistent header stored at the beginning of the mmap buffer file.
 *
 * Layout:
 * ```
 * offset  field       type    size
 * 0       magic       byte    1
 * 1       logLen      Long    8
 * 9       logPathLen  Int     4
 * 13      logPath     bytes   logPathLen
 * ```
 *
 * The header lets the next process recover the dirty tail left behind when the
 * previous process was killed: it knows how many bytes are valid and which log
 * file they belong to.
 */
internal object LogBufferHeader {
    const val MAGIC: Byte = 0x11

    const val FIXED_SIZE = 1 + 8 + 4

    fun headerSize(logPathLen: Int): Int = FIXED_SIZE + logPathLen

    fun isAvailable(data: ByteArray): Boolean = data.isNotEmpty() && data[0] == MAGIC

    fun writeHeader(buffer: MappedBuffer, logLen: Long, logPath: String) {
        val pathBytes = logPath.encodeToByteArray()
        val header = ByteArray(headerSize(pathBytes.size))
        header[0] = MAGIC
        writeLong(header, 1, logLen)
        writeInt(header, 9, pathBytes.size)
        pathBytes.copyInto(header, FIXED_SIZE)
        buffer.put(0, header, 0, header.size)
    }

    fun readLogLen(buffer: MappedBuffer): Long {
        val head = ByteArray(FIXED_SIZE)
        buffer.get(0, head, 0, FIXED_SIZE)
        return readLong(head, 1)
    }

    fun readLogPath(buffer: MappedBuffer): String {
        val head = ByteArray(FIXED_SIZE)
        buffer.get(0, head, 0, FIXED_SIZE)
        val pathLen = readInt(head, 9)
        if (pathLen <= 0) return ""
        val pathBytes = ByteArray(pathLen)
        buffer.get(FIXED_SIZE.toLong(), pathBytes, 0, pathLen)
        return pathBytes.decodeToString()
    }

    private fun writeLong(dst: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            dst[offset + i] = ((value ushr (i * 8)) and 0xFF).toByte()
        }
    }

    private fun readLong(src: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = value or ((src[offset + i].toLong() and 0xFF) shl (i * 8))
        }
        return value
    }

    private fun writeInt(dst: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) {
            dst[offset + i] = ((value ushr (i * 8)) and 0xFF).toByte()
        }
    }

    private fun readInt(src: ByteArray, offset: Int): Int {
        var value = 0
        for (i in 0 until 4) {
            value = value or ((src[offset + i].toInt() and 0xFF) shl (i * 8))
        }
        return value
    }
}
