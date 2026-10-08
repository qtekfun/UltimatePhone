package com.qtekfun.ultimatephone.feature.spam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.screening.SpamVerdict
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WhyFlaggedState(
    /** The number as the user reads it. */
    val displayNumber: String,
    val verdict: SpamVerdict? = null,
    val inAllowed: Boolean = false,
    val isLoading: Boolean = true
)

/** Explains the latest recorded decision for one number and lets the user overrule it. */
@HiltViewModel
class WhyFlaggedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val actions: SpamNumberActions,
    normalizer: PhoneNormalizer,
    regions: RegionProvider
) : ViewModel() {
    private val number: String = savedStateHandle.get<String>(NUMBER_ARG).orEmpty()
    private val display = normalizer.formatForDisplay(number, regions.defaultRegion())

    val state: StateFlow<WhyFlaggedState> = actions.observeState(number)
        .map { WhyFlaggedState(display, it.verdict, it.inAllowed, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WhyFlaggedState(display))

    /** "Not spam": the number goes to the allowed numbers and the spam list entry for it, if any, goes away. */
    fun notSpam() {
        viewModelScope.launch { actions.allow(number) }
    }

    fun removeFromAllowed() {
        viewModelScope.launch { actions.removeFromAllowed(number) }
    }

    companion object {
        const val NUMBER_ARG = "number"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
