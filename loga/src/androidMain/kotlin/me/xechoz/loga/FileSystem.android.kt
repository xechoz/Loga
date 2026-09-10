package me.xechoz.loga

import java.io.File
import java.io.RandomAccessFile

actual object FileSystem {
    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun size(path: String): Long = File(path).length()

    actual fun append(path: String, data: ByteArray, offset: Int, length: Int) {
        RandomAccessFile(path, "rw").use { raf ->
            raf.seek(raf.length())
            raf.write(data, offset, length)
        }
    }

    actual fun read(path: String, offset: Long, length: Int): ByteArray {
        val result = ByteArray(length)
        RandomAccessFile(path, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(result)
        }
        return result
    }

    actual fun list(dir: String): List<String> =
        File(dir).list()?.toList() ?: emptyList()

    actual fun delete(path: String): Boolean = File(path).delete()

    actual fun mkdirs(path: String): Boolean {
        val dir = File(path)
        return dir.exists() || dir.mkdirs()
    }
}
