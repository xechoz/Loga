package me.xechoz.loga

/**
 * A self-contained flush task: it owns the target path and a snapshot of the
 * bytes to write. Because it does not reference the [LogBuffer] or its mapping,
 * it stays valid even after the buffer has been released.
 */
internal class FlushBuffer(
    val path: String,
    val data: ByteArray,
    val length: Int,
) {
    fun flushToFile() {
        if (length <= 0) return
        FileSystem.append(path, data, 0, length)
    }
}
