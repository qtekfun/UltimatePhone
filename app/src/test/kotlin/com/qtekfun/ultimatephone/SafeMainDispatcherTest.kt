package com.qtekfun.ultimatephone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeMainDispatcherTest {
    @Test
    fun mainDispatcherDoesNotFailWhenNoTestDispatcherIsSet() {
        val result = runBlocking { withContext(Dispatchers.Main) { 21 * 2 } }
        assertEquals(42, result)
    }
}
