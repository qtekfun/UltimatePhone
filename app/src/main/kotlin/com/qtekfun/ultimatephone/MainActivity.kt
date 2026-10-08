package com.qtekfun.ultimatephone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qtekfun.ultimatephone.core.designsystem.UltimatePhoneTheme
import com.qtekfun.ultimatephone.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var dialNumber by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dialNumber = numberFrom(intent)
        setContent {
            UltimatePhoneTheme {
                AppNavHost(initialDialNumber = dialNumber)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dialNumber = numberFrom(intent)
    }

    /** `tel:` links and the system "dial" action carry the number the user wants to call. */
    private fun numberFrom(intent: Intent): String? = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.takeIf { it.isNotBlank() }
}
