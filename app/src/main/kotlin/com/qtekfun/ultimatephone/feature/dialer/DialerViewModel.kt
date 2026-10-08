package com.qtekfun.ultimatephone.feature.dialer

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.contacts.DialerSuggestion
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.e164OrNull
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.RoleController
import com.qtekfun.ultimatephone.core.telecom.SimAccount
import com.qtekfun.ultimatephone.core.telecom.SimRepository
import com.qtekfun.ultimatephone.settings.SettingsRepository
import com.qtekfun.ultimatephone.settings.SimChooser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DialerUiState(
    val number: String = "",
    val displayNumber: String = "",
    val suggestions: List<DialerSuggestion> = emptyList(),
    val sims: List<SimAccount> = emptyList(),
    /** The SIM the next call will use; null when the system decides. */
    val selectedSimKey: String? = null,
    val needsDefaultPhoneApp: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DialerViewModel @Inject constructor(
    private val contacts: ContactsRepository,
    private val simRepository: SimRepository,
    private val settings: SettingsRepository,
    private val callPlacer: CallPlacer,
    private val normalizer: PhoneNormalizer,
    private val regions: RegionProvider,
    private val roles: RoleController,
    @ApplicationContext context: Context
) : ViewModel() {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val number = MutableStateFlow("")
    private val explicitSimKey = MutableStateFlow<String?>(null)
    private val sims = MutableStateFlow(emptyList<SimAccount>())
    private val needsRole = MutableStateFlow(false)

    private val suggestions = number.mapLatest { typed ->
        val digits = DialerInput.searchDigits(typed)
        if (digits.isEmpty() || typed.any { it == '*' || it == '#' }) emptyList() else contacts.suggest(digits)
    }

    val state: StateFlow<DialerUiState> = combine(number, suggestions, sims, explicitSimKey, settings.settings) { typed, found, available, explicit, prefs ->
        val region = regions.defaultRegion()
        val remembered = normalizer.normalize(typed, region).e164OrNull()?.let { prefs.simByNumber[it] }
        DialerUiState(
            number = typed,
            displayNumber = if (typed.length > MIN_FORMAT_LENGTH) normalizer.formatForDisplay(typed, region) else typed,
            suggestions = found,
            sims = available,
            selectedSimKey = if (available.size > 1) SimChooser.choose(explicit, remembered, prefs.defaultSimKey, available.map { it.key }.toSet()) else null
        )
    }.combine(needsRole) { ui, role -> ui.copy(needsDefaultPhoneApp = role) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DialerUiState())

    init {
        refresh()
    }

    /** Re-reads what can change while the app is away: SIMs (permission granted) and the phone role. */
    fun refresh() {
        sims.value = simRepository.accounts()
        needsRole.value = roles.isDialerRoleAvailable() && !roles.isDefaultDialer()
    }

    fun setNumber(value: String) {
        number.value = DialerInput.sanitizePaste(value)
    }

    fun press(key: Char) = number.update { DialerInput.append(it, key) }

    fun pressPlus() = number.update { DialerInput.plusInsteadOfZero(it) }

    fun backspace() = number.update { DialerInput.backspace(it) }

    fun clear() {
        number.value = ""
    }

    fun paste(text: String) {
        number.value = DialerInput.sanitizePaste(text)
    }

    fun selectSim(key: String) {
        explicitSimKey.value = key
    }

    /** Calls the typed number. @return false when there is nothing to call or the call could not be started. */
    fun call(): Boolean {
        val typed = number.value
        if (typed.isBlank()) return false
        val available = sims.value
        val sim = if (isEmergency(typed) || available.size < 2) {
            null
        } else {
            available.firstOrNull { it.key == state.value.selectedSimKey }
        }
        val explicit = explicitSimKey.value
        val e164 = normalizer.normalize(typed, regions.defaultRegion()).e164OrNull()
        if (explicit != null && e164 != null) viewModelScope.launch { settings.rememberSim(e164, explicit) }
        return callPlacer.placeCall(typed, sim)
    }

    /** Emergency numbers are dialled at once, on whatever SIM the system picks. */
    @Suppress("DEPRECATION") // The framework check needs READ_PHONE_STATE; the old static one works without it.
    private fun isEmergency(typed: String): Boolean = try {
        telephony.isEmergencyNumber(typed)
    } catch (_: SecurityException) {
        PhoneNumberUtils.isEmergencyNumber(typed)
    }

    fun callSuggestion(suggestion: DialerSuggestion): Boolean {
        number.value = DialerInput.sanitizePaste(suggestion.number)
        return call()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MIN_FORMAT_LENGTH = 4
    }
}
