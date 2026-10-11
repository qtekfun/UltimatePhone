package com.qtekfun.ultimatephone

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationTest {
    @Test
    fun `locking keeps the screen in portrait`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, orientationFor(lockPortrait = true))
    }

    @Test
    fun `not locking leaves the orientation to the device`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, orientationFor(lockPortrait = false))
    }
}
