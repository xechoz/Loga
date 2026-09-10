package me.xechoz.loga

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.posix.MAP_FAILED
import platform.posix.MAP_SHARED
import platform.posix.MS_SYNC
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.PROT_READ
import platform.posix.PROT_WRITE
import platform.posix.close
import platform.posix.ftruncate
import platform.posix.memcpy
import platform.posix.mmap
import platform.posix.msync
import platform.posix.munmap
import platform.posix.off_t
import platform.posix.open

@OptIn(ExperimentalForeignApi::class)
actual class MappedBuffer internal constructor(
    private val fd: Int,
    private val pointer: CPointer<ByteVar>,
    actual val size: Long,
) {
    actual fun put(position: Long, src: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        src.usePinned { pinned ->
            memcpy(pointer.plus(position), pinned.addressOf(offset), length.convert())
        }
    }

    actual fun get(position: Long, dst: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        dst.usePinned { pinned ->
            memcpy(pinned.addressOf(offset), pointer.plus(position), length.convert())
        }
    }

    actual fun force() {
        msync(pointer, size.convert(), MS_SYNC)
    }

    actual fun close() {
        munmap(pointer, size.convert())
        close(fd)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun openMappedBuffer(path: String, size: Long): MappedBuffer {
    val fd = open(path, O_RDWR or O_CREAT, 0x1B6)
    if (fd < 0) error("failed to open $path")
    if (ftruncate(fd, size.convert<off_t>()) != 0) {
        close(fd)
        error("failed to resize $path to $size")
    }
    val raw = mmap(null, size.convert(), PROT_READ or PROT_WRITE, MAP_SHARED, fd, 0)
    if (raw == null || raw == MAP_FAILED) {
        close(fd)
        error("failed to mmap $path")
    }
    return MappedBuffer(fd, raw.reinterpret(), size)
}
