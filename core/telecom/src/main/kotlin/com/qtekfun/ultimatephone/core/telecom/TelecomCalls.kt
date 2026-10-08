package com.qtekfun.ultimatephone.core.telecom

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.qtekfun.ultimatephone.core.phonenumber.LibPhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.PhoneMask
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide state of the calls Telecom hands to [UltimateInCallService]. The service feeds it, the UI observes it
 * through [CallController]. One object because the service and the in-call screen live in the same process.
 */
object TelecomCalls : CallController {
    private val lock = Any()
    private val live = LinkedHashMap<String, Call>()
    private val ids = java.util.IdentityHashMap<Call, String>()
    private val nextId = java.util.concurrent.atomic.AtomicLong()
    private val labels = ConcurrentHashMap<String, CallerLabel>()

    /** Display form of each live call's number, formatted once per call instead of on every state change. */
    private val displays = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val normalizer: PhoneNormalizer = LibPhoneNormalizer.shared
    private val callsState = MutableStateFlow<List<CallInfo>>(emptyList())
    private val audioState = MutableStateFlow(AudioState.Default)

    @Volatile
    private var service: InCallService? = null

    @Volatile
    private var sims: SimRepository? = null

    @Volatile
    private var regions: RegionProvider? = null

    /** Set once by the app at start-up; looks callers up without the telecom module knowing about contacts. */
    @Volatile
    var labelResolver: CallerLabelResolver? = null

    override val calls: StateFlow<List<CallInfo>> get() = callsState
    override val audio: StateFlow<AudioState> get() = audioState

    internal fun attach(service: InCallService, context: Context) {
        this.service = service
        sims = TelecomSimRepository(context.applicationContext)
        regions = SimRegionProvider(context.applicationContext)
    }

    internal fun detach(service: InCallService) {
        if (this.service === service) this.service = null
    }

    internal fun onCallAdded(call: Call) {
        val id = idOf(call)
        synchronized(lock) { live[id] = call }
        rebuild()
        val number = call.details.handle?.schemeSpecificPart
        val resolver = labelResolver
        if (number != null && resolver != null) {
            scope.launch {
                resolver.resolve(number)?.let {
                    labels[id] = it
                    rebuild()
                }
            }
        }
    }

    internal fun onCallChanged() = rebuild()

    internal fun onCallRemoved(call: Call) {
        val id = idOf(call)
        synchronized(lock) {
            live.remove(id)
            ids.remove(call)
        }
        labels.remove(id)
        displays.remove(id)
        rebuild()
    }

    internal fun onAudioChanged(state: CallAudioState) {
        audioState.value = AudioState(
            route = AudioRouteMapper.route(state.route),
            available = AudioRouteMapper.available(state.supportedRouteMask),
            muted = state.isMuted
        )
    }

    /** Telecom's own call id is not public API on Android 12, so each call gets an id for as long as it lives. */
    private fun idOf(call: Call): String = synchronized(lock) { ids.getOrPut(call) { "call-${nextId.incrementAndGet()}" } }

    private fun rebuild() {
        val snapshot = synchronized(lock) { live.toMap() }
        callsState.value = snapshot.filterValues { it.parent == null }.map { (id, call) -> toInfo(id, call) }
    }

    private fun toInfo(id: String, call: Call): CallInfo {
        val details = call.details
        val number = details.handle?.schemeSpecificPart
        val label = labels[id]
        return CallInfo(
            id = id,
            status = CallStatusMapper.fromState(details.state),
            number = number,
            displayNumber = number?.let { displays.getOrPut(id) { normalizer.formatForDisplay(it, regions?.defaultRegion()) } }.orEmpty(),
            incoming = details.callDirection == Call.Details.DIRECTION_INCOMING,
            sim = sims?.find(details.accountHandle),
            connectedAtMillis = details.connectTimeMillis,
            canHold = details.can(Call.Details.CAPABILITY_HOLD),
            canMerge = details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            isConference = call.children.isNotEmpty(),
            participants = call.children.size,
            contactName = label?.name ?: details.contactDisplayName,
            contactPhotoUri = label?.photoUri,
            disconnectLabel = details.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() },
            isBusiness = label?.isBusiness == true,
            businessCategory = label?.businessCategory,
            businessIcon = label?.businessIcon
        )
    }

    private fun callFor(id: String): Call? = synchronized(lock) { live[id] }

    override fun answer(id: String) {
        callFor(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    override fun reject(id: String) {
        callFor(id)?.reject(false, null)
    }

    override fun hangup(id: String) {
        callFor(id)?.disconnect()
    }

    override fun setHold(id: String, hold: Boolean) {
        val call = callFor(id) ?: return
        if (hold) call.hold() else call.unhold()
    }

    override fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    @Suppress("DEPRECATION") // The route API that works on every Android 12+ build; the endpoint API needs Android 14.
    override fun setRoute(route: AudioRoute) {
        service?.setAudioRoute(AudioRouteMapper.toMask(route))
    }

    override fun playDtmf(id: String, digit: Char) {
        callFor(id)?.playDtmfTone(digit)
    }

    override fun stopDtmf(id: String) {
        callFor(id)?.stopDtmfTone()
    }

    override fun mergeCalls() {
        val all = synchronized(lock) { live.values.toList() }
        all.firstOrNull { it.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) }?.let {
            it.mergeConference()
            return
        }
        val first = all.firstOrNull() ?: return
        all.firstOrNull { other -> other !== first && first.conferenceableCalls.contains(other) }?.let { first.conference(it) }
    }

    override fun swapCalls() {
        val held = synchronized(lock) { live.values.firstOrNull { it.details.state == Call.STATE_HOLDING } }
        held?.unhold()
    }

    /** For logs: a call described without its number. */
    internal fun describe(call: Call): String = "${PhoneMask.mask(call.details.handle?.schemeSpecificPart)} state=${call.details.state}"
}
