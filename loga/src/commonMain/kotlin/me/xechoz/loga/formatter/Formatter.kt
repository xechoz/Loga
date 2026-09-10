package me.xechoz.loga.formatter

fun interface Formatter {
    fun format(level: Int, tag: String, msg: String): String
}
