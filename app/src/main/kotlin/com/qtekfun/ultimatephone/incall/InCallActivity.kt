package com.qtekfun.ultimatephone.incall

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.qtekfun.ultimatephone.MainActivity
import com.qtekfun.ultimatephone.core.designsystem.UltimatePhoneTheme
import com.qtekfun.ultimatephone.core.telecom.InCallIntents
import dagger.hilt.android.AndroidEntryPoint

/** The call screen. Shown over the lock screen for incoming calls; closes itself shortly after the last call ends. */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {
    private val viewModel: InCallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            UltimatePhoneTheme {
                InCallScreen(
                    viewModel = viewModel,
                    onAddCall = { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    onFinished = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(InCallIntents.EXTRA_ANSWER, false)) {
            intent.removeExtra(InCallIntents.EXTRA_ANSWER)
            viewModel.answerRinging()
        }
    }
}
