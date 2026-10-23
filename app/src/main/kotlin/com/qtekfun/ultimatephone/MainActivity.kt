package com.qtekfun.ultimatephone

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.core.designsystem.UltimatePhoneTheme
import com.qtekfun.ultimatephone.navigation.AppNavHost
import com.qtekfun.ultimatephone.navigation.WhyFlaggedLink
import com.qtekfun.ultimatephone.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settings: SettingsRepository

    private var dialNumber by mutableStateOf<String?>(null)
    private var whyNumber by mutableStateOf<String?>(null)

    /** Counts the requests to show the call history, so each one opens it even when the previous one already did. */
    private var historyRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dialNumber = numberFrom(intent)
        whyNumber = WhyFlaggedLink.consume(intent)
        if (opensHistory(intent)) historyRequests++
        setContent {
            val prefs by settings.settings.collectAsStateWithLifecycle()
            LaunchedEffect(prefs.lockPortrait) { requestedOrientation = orientationFor(prefs.lockPortrait) }
            UltimatePhoneTheme(mode = prefs.themeMode, useSystemColors = prefs.useSystemColors) {
                AppNavHost(
                    initialDialNumber = dialNumber,
                    whyFlaggedNumber = whyNumber,
                    onWhyFlaggedHandled = { whyNumber = null },
                    historyRequests = historyRequests
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dialNumber = numberFrom(intent)
        WhyFlaggedLink.consume(intent)?.let { whyNumber = it }
        if (opensHistory(intent)) historyRequests++
    }

    /** The system's missed-call notification (and other apps) open the call history with this intent. */
    private fun opensHistory(intent: Intent): Boolean = intent.action == Intent.ACTION_VIEW && intent.type == CallLog.Calls.CONTENT_TYPE

    /** `tel:` links and the system "dial" action carry the number the user wants to call. */
    private fun numberFrom(intent: Intent): String? = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.takeIf { it.isNotBlank() }
}

/** Portrait when the user locked the orientation, otherwise whatever the device decides. */
fun orientationFor(lockPortrait: Boolean): Int = if (lockPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
