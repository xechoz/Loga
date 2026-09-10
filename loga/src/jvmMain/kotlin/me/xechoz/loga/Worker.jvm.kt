package me.xechoz.loga

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

internal actual class Worker actual constructor() {
    private val queue = LinkedBlockingQueue<() -> Unit>()
    @Volatile
    private var running = true

    private val thread = Thread({
        while (running || queue.isNotEmpty()) {
            val task = queue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            task?.invoke()
        }
    }, "loga-flush").apply {
        isDaemon = true
        start()
    }

    actual fun submit(task: () -> Unit) {
        queue.offer(task)
    }

    actual fun submitAndWait(task: () -> Unit) {
        val latch = CountDownLatch(1)
        queue.offer {
            try {
                task()
            } finally {
                latch.countDown()
            }
        }
        latch.await()
    }

    actual fun shutdown() {
        running = false
        thread.join()
    }
}
