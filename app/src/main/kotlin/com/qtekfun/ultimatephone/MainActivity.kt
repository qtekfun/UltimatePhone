package com.qtekfun.ultimatephone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dialNumber = numberFrom(intent)
        whyNumber = WhyFlaggedLink.consume(intent)
        setContent {
            val prefs by settings.settings.collectAsStateWithLifecycle()
            UltimatePhoneTheme(mode = prefs.themeMode, useSystemColors = prefs.useSystemColors) {
                AppNavHost(initialDialNumber = dialNumber, whyFlaggedNumber = whyNumber, onWhyFlaggedHandled = { whyNumber = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dialNumber = numberFrom(intent)
        WhyFlaggedLink.consume(intent)?.let { whyNumber = it }
    }

    /** `tel:` links and the system "dial" action carry the number the user wants to call. */
    private fun numberFrom(intent: Intent): String? = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.takeIf { it.isNotBlank() }
}
