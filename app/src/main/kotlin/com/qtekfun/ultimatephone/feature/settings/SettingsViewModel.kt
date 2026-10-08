package com.qtekfun.ultimatephone.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.designsystem.ThemeMode
import com.qtekfun.ultimatephone.core.telecom.CapabilityProbe
import com.qtekfun.ultimatephone.core.telecom.DeviceCapabilities
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.RoleController
import com.qtekfun.ultimatephone.core.telecom.SimAccount
import com.qtekfun.ultimatephone.core.telecom.SimRepository
import com.qtekfun.ultimatephone.settings.SettingsRepository
import com.qtekfun.ultimatephone.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val sims: List<SimAccount> = emptyList(),
    val isDefaultPhoneApp: Boolean = false,
    val phoneRoleAvailable: Boolean = true,
    val effectiveRegion: String = "",
    val capabilities: DeviceCapabilities? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val simRepository: SimRepository,
    private val roles: RoleController,
    private val regions: RegionProvider
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> get() = _state

    init {
        viewModelScope.launch {
            repository.settings.collect { refresh(it) }
        }
    }

    /** Re-reads what can change while the app is away (role, SIMs, permissions). */
    fun refresh(settings: UserSettings = repository.settings.value) {
        _state.value = SettingsUiState(
            settings = settings,
            sims = simRepository.accounts(),
            isDefaultPhoneApp = roles.isDefaultDialer(),
            phoneRoleAvailable = roles.isDialerRoleAvailable(),
            effectiveRegion = regions.defaultRegion(),
            capabilities = _state.value.capabilities
        )
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            val caps = withContext(Dispatchers.Default) { CapabilityProbe.run(context, simRepository) }
            _state.value = _state.value.copy(capabilities = caps)
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }

    fun setDefaultSim(key: String?) = viewModelScope.launch { repository.setDefaultSim(key) }

    fun setRegion(region: String?) = viewModelScope.launch { repository.setRegionOverride(region) }
}
