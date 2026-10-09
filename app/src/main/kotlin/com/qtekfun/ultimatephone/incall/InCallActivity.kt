package com.qtekfun.ultimatephone.incall

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.MainActivity
import com.qtekfun.ultimatephone.core.designsystem.UltimatePhoneTheme
import com.qtekfun.ultimatephone.core.telecom.InCallIntents
import com.qtekfun.ultimatephone.navigation.WhyFlaggedLink
import com.qtekfun.ultimatephone.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The call screen. Shown over the lock screen for incoming calls; closes itself shortly after the last call ends. */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {
    private val viewModel: InCallViewModel by viewModels()

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            // The call screen follows the same light/dark and colour choices as the rest of the app.
            val prefs by settings.settings.collectAsStateWithLifecycle()
            UltimatePhoneTheme(mode = prefs.themeMode, useSystemColors = prefs.useSystemColors) {
                InCallScreen(
                    viewModel = viewModel,
                    onAddCall = { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    onFinished = { finish() },
                    onWhyFlagged = ::openWhyFlagged
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /**
     * Opens the explanation in the main task; the call carries on. On a locked phone the system asks for the unlock first and
     * the explanation opens only once it succeeds, so nothing about the number is shown to someone who cannot unlock.
     */
    private fun openWhyFlagged(number: String) {
        val open = { startActivity(WhyFlaggedLink.intent(this, number)) }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard != null && keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = open()
                }
            )
        } else {
            open()
        }
    }

    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(InCallIntents.EXTRA_ANSWER, false)) {
            intent.removeExtra(InCallIntents.EXTRA_ANSWER)
            viewModel.answerRinging()
        }
    }
}
