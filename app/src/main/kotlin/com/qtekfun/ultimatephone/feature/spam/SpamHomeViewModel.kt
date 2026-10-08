package com.qtekfun.ultimatephone.feature.spam

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.screening.RULE_ES_400_COMMERCIAL
import com.qtekfun.ultimatephone.screening.ScreeningStats
import com.qtekfun.ultimatephone.screening.SpamPrefs
import com.qtekfun.ultimatephone.screening.SpamSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The actions a user can pick for a flagged call. Informational results use the same three. */
val SELECTABLE_ACTIONS = listOf(SpamAction.WARN, SpamAction.SILENCE, SpamAction.REJECT)

/** The levels with their own row in the table, in display order. */
val TABLE_LEVELS = listOf(SpamLevel.OWN, SpamLevel.COMMUNITY, SpamLevel.RULE)

data class SpamHomeState(
    val roleAvailable: Boolean = false,
    val roleHeld: Boolean = false,
    val prefs: SpamPrefs = SpamPrefs(),
    /** Slowest recent screening in milliseconds, for the diagnostics line; null until a call was screened. */
    val slowestScreeningMillis: Long? = null
) {
    val commercialRuleAction: SpamAction get() = prefs.actionOfRule(RULE_ES_400_COMMERCIAL)
}

@HiltViewModel
class SpamHomeViewModel @Inject constructor(private val settings: SpamSettingsRepository, private val role: ScreeningRole, private val stats: ScreeningStats) :
    ViewModel() {
    private data class RoleSnapshot(val available: Boolean, val held: Boolean, val slowest: Long?)

    private val roleSnapshot = MutableStateFlow(readRole())

    val state: StateFlow<SpamHomeState> = combine(settings.prefs, roleSnapshot) { prefs, snapshot ->
        SpamHomeState(snapshot.available, snapshot.held, prefs, snapshot.slowest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SpamHomeState(prefs = settings.prefs.value))

    private fun readRole() = RoleSnapshot(role.isAvailable, role.isHeld, stats.slowestRecentMillis())

    /** Re-reads the role, for when the user comes back from the system dialog or from the system settings. */
    fun refreshRole() {
        roleSnapshot.update { readRole() }
    }

    fun roleIntent(): Intent? = role.requestIntent()

    fun setLevelAction(level: SpamLevel, action: SpamAction) {
        viewModelScope.launch { settings.setLevelAction(level, action) }
    }

    fun setCommercialRuleAction(action: SpamAction) {
        viewModelScope.launch { settings.setRuleAction(RULE_ES_400_COMMERCIAL, action) }
    }

    fun setBusinessNotSpam(value: Boolean) {
        viewModelScope.launch { settings.setIdentifiedBusinessIsNotSpam(value) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
