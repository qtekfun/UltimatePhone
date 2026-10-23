package com.qtekfun.ultimatephone.core.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDialTest {
    @Test
    fun theNetworkPartStopsAtTheFirstPauseOrWait() {
        assertEquals("912345678", PostDial.networkPart("912345678,22;5"))
        assertEquals("912345678", PostDial.networkPart("912345678;22"))
        assertEquals("912345678", PostDial.networkPart("912345678"))
        assertEquals("", PostDial.networkPart(""))
    }

    @Test
    fun detectsPostDialDigits() {
        assertTrue(PostDial.hasPostDial("1,2"))
        assertTrue(PostDial.hasPostDial("1;2"))
        assertFalse(PostDial.hasPostDial("*#06#"))
    }
}
