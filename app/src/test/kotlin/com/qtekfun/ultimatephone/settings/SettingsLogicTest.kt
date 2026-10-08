package com.qtekfun.ultimatephone.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsLogicTest {
    private val available = setOf("simA", "simB")

    @Test
    fun explicitChoiceBeatsRememberedAndDefault() {
        assertEquals("simB", SimChooser.choose("simB", "simA", "simA", available))
    }

    @Test
    fun rememberedBeatsDefault() {
        assertEquals("simB", SimChooser.choose(null, "simB", "simA", available))
    }

    @Test
    fun defaultIsUsedLast() {
        assertEquals("simA", SimChooser.choose(null, null, "simA", available))
    }

    @Test
    fun unavailableSimsAreSkipped() {
        assertEquals("simA", SimChooser.choose("gone", "gone2", "simA", available))
        assertNull(SimChooser.choose("gone", null, null, available))
        assertNull(SimChooser.choose(null, null, null, emptySet()))
    }

    @Test
    fun simMemoryRoundTrips() {
        val map = mapOf("+34612345678" to "com.android.phone/.Service/8934", "+4915112345678" to "x/y/1")
        assertEquals(map, SimMemoryCodec.decode(SimMemoryCodec.encode(map)))
    }

    @Test
    fun simMemoryIgnoresGarbage() {
        assertEquals(emptyMap<String, String>(), SimMemoryCodec.decode(""))
        assertEquals(mapOf("a" to "b"), SimMemoryCodec.decode("garbage\na\tb\n\t\nc\t"))
    }
}
