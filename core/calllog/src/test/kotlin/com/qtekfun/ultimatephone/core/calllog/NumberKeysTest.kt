package com.qtekfun.ultimatephone.core.calllog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NumberKeysTest {
    private val keys = NumberKeys(FakeNormalizer(), FakeRegion())

    @Test
    fun validNumbersUseE164() {
        assertEquals("+34600111222", keys.keyOf("600 111 222"))
        assertEquals(keys.keyOf("600111222"), keys.keyOf("+34 600 111 222"))
    }

    @Test
    fun unparseableNumbersFallBackToDigits() {
        assertEquals("112", keys.keyOf("112"))
        assertEquals("1234", keys.keyOf("12-34"))
        assertNotEquals(keys.keyOf("112"), keys.keyOf("091"))
    }

    @Test
    fun hiddenNumbersHaveAnEmptyKey() {
        assertEquals("", keys.keyOf(""))
    }

    @Test
    fun keyFunctionReadsTheRegionOnce() {
        var reads = 0
        val counting = NumberKeys(
            FakeNormalizer(),
            object : com.qtekfun.ultimatephone.core.telecom.RegionProvider {
                override fun defaultRegion(): String {
                    reads++
                    return "ES"
                }
            }
        )
        val key = counting.keyFunction()
        key("600111222")
        key("600111333")
        assertEquals(1, reads)
    }
}
