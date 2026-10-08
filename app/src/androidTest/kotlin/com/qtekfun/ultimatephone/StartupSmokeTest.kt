package com.qtekfun.ultimatephone

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.qtekfun.ultimatephone.core.telecom.InCallIntents
import com.qtekfun.ultimatephone.incall.InCallActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The checks that would have caught the first-run crash of 0.1.0-rc1 (a ContentObserver registered in `Application.onCreate`
 * before READ_CONTACTS was granted). If the Application or an activity fails to start, the whole instrumentation run fails.
 * They run in CI on an AOSP emulator without Google apps and on one with Google APIs.
 */
@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {
    @Test
    fun applicationStartsWithoutAnyPermission() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(app.applicationContext is UltimatePhoneApp)
        listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE
        ).forEach { permission ->
            // Not granted by the test harness: the whole point is to start in this state.
            assertEquals(
                "expected $permission to be denied for this test",
                android.content.pm.PackageManager.PERMISSION_DENIED,
                app.checkSelfPermission(permission)
            )
        }
    }

    @Test
    fun mainActivityStaysResumedWithoutPermissions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(SETTLE_MS)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun callScreenOpensEvenWhenThereIsNoCall() {
        val intent = Intent(InCallIntents.ACTION_IN_CALL).setClassName(
            ApplicationProvider.getApplicationContext<Context>().packageName,
            InCallActivity::class.java.name
        )
        ActivityScenario.launch<InCallActivity>(intent).use { scenario ->
            Thread.sleep(SETTLE_MS)
            // With no call it shows "call ended" and closes itself; either way it must not have crashed.
            assertTrue(scenario.state == Lifecycle.State.RESUMED || scenario.state == Lifecycle.State.DESTROYED)
        }
    }

    private companion object {
        const val SETTLE_MS = 3_000L
    }
}

/** Same start-up with the read permissions granted, which turns on the contact, call log and SIM code paths. */
@RunWith(AndroidJUnit4::class)
class StartupWithPermissionsSmokeTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE
    )

    @Test
    fun mainActivityStaysResumedWithPermissions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(SETTLE_MS)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    private companion object {
        const val SETTLE_MS = 3_000L
    }
}
