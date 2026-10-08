package com.qtekfun.ultimatephone.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.data.DataSettings
import com.qtekfun.ultimatephone.data.DataSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Tells the navigation host whether the first-run flow still has to be shown. `null` while the setting is being read. */
@HiltViewModel
class OnboardingGateViewModel @Inject constructor(settings: DataSettingsRepository) : ViewModel() {
    val completed: StateFlow<Boolean?> = settings.settings.map<DataSettings, Boolean?> { it.onboardingCompleted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
