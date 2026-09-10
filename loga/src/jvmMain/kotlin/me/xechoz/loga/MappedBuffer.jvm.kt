package me.xechoz.loga

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

actual class MappedBuffer internal constructor(
    private val raf: RandomAccessFile,
    private val channel: FileChannel,
    private val buffer: MappedByteBuffer,
) {
    actual val size: Long get() = buffer.capacity().toLong()

    actual fun put(position: Long, src: ByteArray, offset: Int, length: Int) {
        val dup = buffer.duplicate()
        dup.position(position.toInt())
        dup.put(src, offset, length)
    }

    actual fun get(position: Long, dst: ByteArray, offset: Int, length: Int) {
        val dup = buffer.duplicate()
        dup.position(position.toInt())
        dup.get(dst, offset, length)
    }

    actual fun force() {
        buffer.force()
    }

    actual fun close() {
        try {
            channel.close()
        } finally {
            raf.close()
        }
    }
}

actual fun openMappedBuffer(path: String, size: Long): MappedBuffer {
    val raf = RandomAccessFile(path, "rw")
    raf.setLength(size)
    val channel = raf.channel
    val buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, size)
    return MappedBuffer(raf, channel, buffer)
}
