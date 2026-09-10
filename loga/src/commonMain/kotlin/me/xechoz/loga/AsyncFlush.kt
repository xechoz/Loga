package me.xechoz.loga

/**
 * Single-worker asynchronous file writer.
 *
 * Producers submit self-contained [FlushBuffer] tasks; a single background
 * worker drains them in FIFO order. This keeps the logging thread free of disk
 * I/O while guaranteeing ordering. [shutdown] blocks until the queue is drained
 * so that no data is lost on release.
 */
internal class AsyncFlush {
    private val worker = Worker()

    fun submit(task: FlushBuffer) {
        worker.submit { task.flushToFile() }
    }

    /**
     * Blocks until every task submitted so far has been written.
     */
    fun await() {
        worker.submitAndWait { }
    }

    fun shutdown() {
        worker.shutdown()
    }
}
