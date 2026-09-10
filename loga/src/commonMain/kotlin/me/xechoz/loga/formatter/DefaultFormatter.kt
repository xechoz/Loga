package me.xechoz.loga.formatter

import me.xechoz.loga.Level

object DefaultFormatter : Formatter {
    override fun format(level: Int, tag: String, msg: String): String {
        return "${Level.letter(level)}/$tag: $msg\n"
    }
}
