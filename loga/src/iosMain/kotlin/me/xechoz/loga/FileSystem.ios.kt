package me.xechoz.loga

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.usePinned
import platform.posix.O_APPEND
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.closedir
import platform.posix.close
import platform.posix.lseek
import platform.posix.mkdir
import platform.posix.open
import platform.posix.opendir
import platform.posix.read
import platform.posix.readdir
import platform.posix.remove
import platform.posix.write

@OptIn(ExperimentalForeignApi::class)
actual object FileSystem {
    actual fun exists(path: String): Boolean {
        val fd = open(path, O_RDONLY)
        if (fd < 0) return false
        close(fd)
        return true
    }

    actual fun size(path: String): Long {
        val fd = open(path, O_RDONLY)
        if (fd < 0) return 0L
        try {
            return lseek(fd, 0L, SEEK_END)
        } finally {
            close(fd)
        }
    }

    actual fun append(path: String, data: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val fd = open(path, O_RDWR or O_CREAT or O_APPEND, 0x1B6.toUShort())
        if (fd < 0) return
        try {
            data.usePinned { pinned ->
                var written = 0
                while (written < length) {
                    val n = write(fd, pinned.addressOf(offset + written), (length - written).convert())
                    if (n <= 0) break
                    written += n.toInt()
                }
            }
        } finally {
            close(fd)
        }
    }

    actual fun read(path: String, offset: Long, length: Int): ByteArray {
        val result = ByteArray(length)
        val fd = open(path, O_RDONLY)
        if (fd < 0) return result
        try {
            lseek(fd, offset, SEEK_SET)
            result.usePinned { pinned ->
                var total = 0
                while (total < length) {
                    val n = read(fd, pinned.addressOf(total), (length - total).convert())
                    if (n <= 0) break
                    total += n.toInt()
                }
            }
        } finally {
            close(fd)
        }
        return result
    }

    actual fun list(dir: String): List<String> {
        val result = mutableListOf<String>()
        val dp = opendir(dir) ?: return result
        try {
            while (true) {
                val entry = readdir(dp) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name != "." && name != "..") result.add(name)
            }
        } finally {
            closedir(dp)
        }
        return result
    }

    actual fun delete(path: String): Boolean = remove(path) == 0

    actual fun mkdirs(path: String): Boolean {
        if (exists(path)) return true
        return mkdir(path, 0x1FF.toUShort()) == 0
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<ByteVar>.toKString(): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (true) {
        val b = this[i]
        if (b == 0.toByte()) break
        bytes.add(b)
        i++
    }
    return bytes.toByteArray().decodeToString()
}
