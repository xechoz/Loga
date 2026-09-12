package me.xechoz.loga.demo

import kotlin.concurrent.thread

actual fun crashInBackgroundThread() {
    thread(name = "demo-crash") { throw IllegalStateException("demo crash") }
}

actual fun crashInMainThread() {
    throw IllegalStateException("demo crash (main)")
}
