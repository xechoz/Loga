package me.xechoz.loga

/**
 * Minimal cross-platform file operations needed by the engine.
 * Kept intentionally small: append bytes, read bytes, list, delete, size.
 */
expect object FileSystem {
    fun exists(path: String): Boolean

    fun size(path: String): Long

    fun append(path: String, data: ByteArray, offset: Int, length: Int)

    fun read(path: String, offset: Long, length: Int): ByteArray

    fun list(dir: String): List<String>

    fun delete(path: String): Boolean

    fun mkdirs(path: String): Boolean
}
