package me.xechoz.loga

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal actual class Worker actual constructor() {
    private val queue = Channel<() -> Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        scope.launch {
            for (task in queue) {
                task()
            }
        }
    }

    actual fun submit(task: () -> Unit) {
        queue.trySend(task)
    }

    actual fun submitAndWait(task: () -> Unit) {
        val done = CompletableDeferred<Unit>()
        queue.trySend {
            try {
                task()
            } finally {
                done.complete(Unit)
            }
        }
        runBlocking { done.await() }
    }

    actual fun shutdown() {
        queue.close()
        runBlocking {
            while (!queue.isEmpty) {
                kotlinx.coroutines.delay(10)
            }
        }
    }
}
