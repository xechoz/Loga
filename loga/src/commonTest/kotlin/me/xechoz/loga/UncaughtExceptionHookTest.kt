package me.xechoz.loga

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UncaughtExceptionHookTest {
    @Test
    fun hookReceivesCrashAndUninstallRestoresPrevious() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val messages = mutableListOf<String>()

        val uninstall = installUncaughtExceptionHook { messages += it }
        try {
            thread(name = "crash-thread") { throw IllegalStateException("boom") }.join()
        } finally {
            uninstall()
        }

        assertEquals(previous, Thread.getDefaultUncaughtExceptionHandler())
        assertEquals(1, messages.size)
        assertTrue(messages.first().contains("crash-thread"))
        assertTrue(messages.first().contains("boom"))
    }
}
