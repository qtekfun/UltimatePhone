package com.qtekfun.ultimatephone.incall

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.AvatarStyled
import com.qtekfun.ultimatephone.core.designsystem.BrandBackground
import com.qtekfun.ultimatephone.core.designsystem.CallActionButton
import com.qtekfun.ultimatephone.core.designsystem.CallButton
import com.qtekfun.ultimatephone.core.designsystem.CallButtonKind
import com.qtekfun.ultimatephone.core.designsystem.CallPillButton
import com.qtekfun.ultimatephone.core.designsystem.PulsingRing
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.StatusChip
import com.qtekfun.ultimatephone.core.designsystem.callColors
import com.qtekfun.ultimatephone.core.designsystem.motionEffectsSpec
import com.qtekfun.ultimatephone.core.designsystem.tabularFigures
import com.qtekfun.ultimatephone.core.telecom.AudioRoute
import com.qtekfun.ultimatephone.core.telecom.CallDurationFormatter
import com.qtekfun.ultimatephone.core.telecom.CallInfo
import com.qtekfun.ultimatephone.core.telecom.CallStatus
import com.qtekfun.ultimatephone.feature.data.businessCategoryLabel
import com.qtekfun.ultimatephone.feature.data.businessIcon
import com.qtekfun.ultimatephone.feature.spam.CallSpamPanel
import com.qtekfun.ultimatephone.feature.spam.CallSpamUi
import com.qtekfun.ultimatephone.recording.RecordingUi
import kotlinx.coroutines.delay

private const val ENDED_DELAY_MS = 1500L
private const val MILLIS_PER_SECOND = 1000L
private val DIAL_KEYS = listOf("123", "456", "789", "*0#")

/** Below this height the screen switches to smaller avatar and controls. */
private val COMPACT_HEIGHT = 600.dp
private val MIN_TOUCH_TARGET = 56.dp
private val HANGUP_MAX_WIDTH = 360.dp

/** What the layout needs to know about the space it has. */
private data class CallLayout(val landscape: Boolean, val compact: Boolean) {
    val avatarSize: Dp get() = if (compact) 72.dp else 132.dp
    val actionSize: Dp get() = if (compact) 60.dp else 68.dp
    val answerSize: Dp get() = if (compact) 72.dp else 88.dp
    val hangupHeight: Dp get() = if (compact) 64.dp else 72.dp
}

@Composable
fun InCallScreen(viewModel: InCallViewModel, onAddCall: () -> Unit, onFinished: () -> Unit, onWhyFlagged: (String) -> Unit = {}) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val audio by viewModel.audio.collectAsStateWithLifecycle()
    val spam by viewModel.spam.collectAsStateWithLifecycle()
    val recording by viewModel.recordingUi.collectAsStateWithLifecycle()
    var showKeypad by remember { mutableStateOf(false) }

    // Once nothing is left, show "call ended" for a moment and close.
    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) {
            delay(ENDED_DELAY_MS)
            onFinished()
        }
    }

    val primary = calls.firstOrNull { it.status == CallStatus.RINGING } ?: calls.firstOrNull { it.status != CallStatus.HOLDING } ?: calls.firstOrNull()
    val other = calls.firstOrNull { it.id != primary?.id }

    // Full screen, also over the lock screen: keep every control clear of the status bar, the cutout and the gesture bar.
    BrandBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (primary == null) {
                CallEnded(modifier = Modifier.align(Alignment.Center))
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val layout = CallLayout(landscape = maxWidth > maxHeight, compact = maxHeight < COMPACT_HEIGHT)
                    val info: @Composable (Modifier) -> Unit = { modifier ->
                        InfoPane(
                            modifier = modifier,
                            call = primary,
                            other = other,
                            spam = spam[primary.id] ?: CallSpamUi(),
                            layout = layout,
                            showKeypad = showKeypad && primary.status != CallStatus.RINGING,
                            onSwap = viewModel::swap,
                            onNotSpam = { viewModel.notSpam(primary) },
                            onMarkSpam = { viewModel.markSpam(primary) },
                            onWhyFlagged = primary.number?.let { number -> { onWhyFlagged(number) } },
                            onDtmfDown = { viewModel.dtmfDown(primary, it) },
                            onDtmfUp = { viewModel.dtmfUp(primary) },
                            recordingStatus = {
                                RecordingStatus(recording, viewModel::stopRecording, viewModel::dismissMicWarning, viewModel::dismissRecordingFailure)
                            }
                        )
                    }
                    val controls: @Composable (Modifier) -> Unit = { modifier ->
                        if (primary.status == CallStatus.RINGING) {
                            RingingControls(layout, modifier, onAnswer = { viewModel.answer(primary) }, onReject = { viewModel.reject(primary) })
                        } else {
                            OngoingControls(
                                modifier = modifier,
                                layout = layout,
                                call = primary,
                                hasOtherCall = other != null,
                                muted = audio.muted,
                                route = audio.route,
                                availableRoutes = audio.available,
                                keypadShown = showKeypad,
                                onMute = viewModel::toggleMute,
                                onKeypad = { showKeypad = !showKeypad },
                                onRoute = viewModel::setRoute,
                                onHold = { viewModel.toggleHold(primary) },
                                onAddCall = onAddCall,
                                onMerge = viewModel::merge,
                                recording = recording,
                                onRecord = { viewModel.toggleRecording(primary) },
                                onHangup = { viewModel.hangup(primary) }
                            )
                        }
                    }
                    if (layout.landscape) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.Medium)) {
                            info(Modifier.weight(1f).fillMaxHeight())
                            Spacer(Modifier.width(Spacing.Medium))
                            controls(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.Medium)) {
                            info(Modifier.weight(1f).fillMaxWidth())
                            controls(Modifier.fillMaxWidth().padding(bottom = Spacing.Medium))
                        }
                    }
                }
            }
        }
    }
}

/** "Call ended": the end icon in a tonal circle and the words, announced politely. */
@Composable
private fun CallEnded(modifier: Modifier) {
    Column(
        modifier = modifier.padding(Spacing.Large).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(44.dp))
        }
        Text(stringResource(R.string.incall_call_ended), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    }
}

/** Who is calling, the state and the spam verdict. It scrolls when the space (or the text) does not fit. */
@Composable
private fun InfoPane(
    modifier: Modifier,
    call: CallInfo,
    other: CallInfo?,
    spam: CallSpamUi,
    layout: CallLayout,
    showKeypad: Boolean,
    onSwap: () -> Unit,
    onNotSpam: () -> Unit,
    onMarkSpam: () -> Unit,
    onWhyFlagged: (() -> Unit)?,
    onDtmfDown: (Char) -> Unit,
    onDtmfUp: () -> Unit,
    recordingStatus: @Composable () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(top = Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        recordingStatus()
        if (other != null) OtherCallCard(other, onSwap = onSwap)
        CallHeader(call, layout, showAvatar = !showKeypad)
        CallSpamPanel(ui = spam, ringing = call.status == CallStatus.RINGING, onNotSpam = onNotSpam, onMarkSpam = onMarkSpam, onWhyFlagged = onWhyFlagged)
        if (showKeypad) {
            Spacer(Modifier.size(Spacing.Medium))
            DtmfKeypad(onDown = onDtmfDown, onUp = onDtmfUp)
        }
    }
}

@Composable
private fun CallHeader(call: CallInfo, layout: CallLayout, showAvatar: Boolean) {
    val elapsed by produceState(0L, call.status, call.connectedAtMillis) {
        while (call.connectedAtMillis > 0 && (call.status == CallStatus.ACTIVE || call.status == CallStatus.HOLDING)) {
            value = (System.currentTimeMillis() - call.connectedAtMillis) / MILLIS_PER_SECOND
            delay(MILLIS_PER_SECOND)
        }
    }
    val ringing = call.status == CallStatus.RINGING
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = if (layout.compact) Spacing.Small else Spacing.Large)) {
        if (showAvatar) {
            // The ring pulses only while the phone rings (and never with reduced motion).
            PulsingRing(active = ringing, extent = if (layout.compact) 12.dp else 24.dp) {
                AvatarStyled(
                    name = call.title.takeIf { call.contactName != null },
                    size = layout.avatarSize,
                    business = call.isBusiness,
                    businessIcon = businessIcon(call.businessIcon)
                )
            }
        }
        // The name is the most important line: display size, up to three lines (large font sizes).
        Text(
            call.title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.Small)
        )
        if (call.contactName != null && call.displayNumber.isNotEmpty()) {
            Text(
                call.displayNumber,
                style = MaterialTheme.typography.titleMedium.tabularFigures(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.XSmall)
            )
        }
        CallChips(call)
        val status = when (call.status) {
            CallStatus.RINGING -> stringResource(R.string.incall_incoming)
            CallStatus.DIALING, CallStatus.CONNECTING -> stringResource(R.string.incall_calling)
            CallStatus.ACTIVE -> CallDurationFormatter.format(elapsed)
            CallStatus.HOLDING -> stringResource(R.string.incall_on_hold, CallDurationFormatter.format(elapsed))
            CallStatus.DISCONNECTING, CallStatus.DISCONNECTED -> call.disconnectLabel ?: stringResource(R.string.incall_call_ended)
            CallStatus.OTHER -> ""
        }
        // Active and on-hold calls show a running duration: that must not be read out every second.
        val ticking = call.status == CallStatus.ACTIVE || call.status == CallStatus.HOLDING
        Text(
            status,
            style = MaterialTheme.typography.titleLarge.tabularFigures(),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            // Ringing, calling and ended are announced by TalkBack as they change; the running duration is not live.
            modifier = Modifier.padding(top = Spacing.Small).semantics { if (!ticking) liveRegion = LiveRegionMode.Polite }
        )
        if (call.isConference) {
            Text(stringResource(R.string.incall_conference, call.participants), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The secondary facts under the name: the business category and the SIM, as small chips. */
@Composable
private fun CallChips(call: CallInfo) {
    val sim = call.sim
    if (!call.isBusiness && sim == null) return
    Row(
        modifier = Modifier.padding(top = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (call.isBusiness) {
            StatusChip(text = stringResource(businessCategoryLabel(call.businessCategory)), icon = businessIcon(call.businessIcon))
        }
        if (sim != null) {
            val label = if (sim.slot >= 0) stringResource(R.string.dialer_sim_slot, sim.slot + 1) else sim.label
            StatusChip(text = label, icon = Icons.Filled.SimCard)
        }
    }
}

@Composable
private fun OtherCallCard(call: CallInfo, onSwap: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.Medium, end = Spacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(if (call.status == CallStatus.RINGING) R.string.incall_waiting else R.string.incall_held, call.title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(vertical = Spacing.Small)
            )
            TextButton(onClick = onSwap, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.incall_swap)) }
        }
    }
}

/** Decline and Answer: big, labelled, at the bottom where the thumb rests. */
@Composable
private fun RingingControls(layout: CallLayout, modifier: Modifier, onAnswer: () -> Unit, onReject: () -> Unit) {
    Row(
        modifier = modifier.padding(vertical = Spacing.Large),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        CallButton(CallButtonKind.Decline, stringResource(R.string.incall_decline), onReject, size = layout.answerSize)
        CallButton(CallButtonKind.Answer, stringResource(R.string.incall_answer), onAnswer, size = layout.answerSize)
    }
}

/** One action of the grid, with its label. */
private class CallAction(val key: String, val content: @Composable RowScope.() -> Unit)

@Composable
private fun OngoingControls(
    modifier: Modifier,
    layout: CallLayout,
    call: CallInfo,
    hasOtherCall: Boolean,
    muted: Boolean,
    route: AudioRoute,
    availableRoutes: Set<AudioRoute>,
    keypadShown: Boolean,
    onMute: () -> Unit,
    onKeypad: () -> Unit,
    onRoute: (AudioRoute) -> Unit,
    onHold: () -> Unit,
    onAddCall: () -> Unit,
    onMerge: () -> Unit,
    recording: RecordingUi,
    onRecord: () -> Unit,
    onHangup: () -> Unit
) {
    val size = layout.actionSize
    val holding = call.status == CallStatus.HOLDING
    val on = stringResource(R.string.incall_state_on)
    val off = stringResource(R.string.incall_state_off)
    fun state(checked: Boolean) = if (checked) on else off
    // Hold and merge only appear when they can do something.
    val actions = buildList {
        add(
            CallAction("mute") {
                CallActionButton(
                    icon = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = stringResource(R.string.incall_mute),
                    onClick = onMute,
                    checked = muted,
                    stateDescription = state(muted),
                    size = size,
                    modifier = Modifier.weight(1f)
                )
            }
        )
        add(
            CallAction("keypad") {
                CallActionButton(
                    icon = Icons.Filled.Dialpad,
                    label = stringResource(R.string.incall_keypad),
                    onClick = onKeypad,
                    checked = keypadShown,
                    stateDescription = state(keypadShown),
                    size = size,
                    modifier = Modifier.weight(1f)
                )
            }
        )
        add(CallAction("audio") { RouteAction(route, availableRoutes, onRoute, size, Modifier.weight(1f)) })
        if (call.canHold || holding) {
            add(
                CallAction("hold") {
                    CallActionButton(
                        icon = if (holding) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        label = stringResource(R.string.incall_hold),
                        onClick = onHold,
                        checked = holding,
                        stateDescription = state(holding),
                        size = size,
                        modifier = Modifier.weight(1f)
                    )
                }
            )
        }
        add(
            CallAction("add") {
                CallActionButton(
                    icon = Icons.Filled.PersonAdd,
                    label = stringResource(R.string.incall_add_call),
                    onClick = onAddCall,
                    size = size,
                    modifier = Modifier.weight(1f)
                )
            }
        )
        if (recording.available || recording.isBusy) {
            add(CallAction("record") { RecordAction(recording, onRecord, size, Modifier.weight(1f)) })
        }
        if (hasOtherCall || call.canMerge || call.isConference) {
            add(
                CallAction("merge") {
                    CallActionButton(
                        icon = if (call.isConference) Icons.AutoMirrored.Filled.CallSplit else Icons.AutoMirrored.Filled.CallMerge,
                        label = stringResource(R.string.incall_merge),
                        onClick = onMerge,
                        checked = call.isConference,
                        stateDescription = state(call.isConference),
                        enabled = hasOtherCall || call.canMerge,
                        size = size,
                        modifier = Modifier.weight(1f)
                    )
                }
            )
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        // The grid gives way (and scrolls) before the hang-up button does.
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            actions.chunked(GRID_COLUMNS).forEach { rowActions ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.Top) {
                    rowActions.forEach { action -> action.content(this) }
                    repeat(GRID_COLUMNS - rowActions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // The most important control: wide, red, centred, at the bottom where the thumb rests.
        CallPillButton(
            kind = CallButtonKind.End,
            label = stringResource(R.string.incall_end_call),
            onClick = onHangup,
            modifier = Modifier.padding(top = Spacing.Large).fillMaxWidth().widthIn(max = HANGUP_MAX_WIDTH),
            height = layout.hangupHeight
        )
    }
}

private const val GRID_COLUMNS = 3

/** One button: toggles the speaker, or opens a menu when Bluetooth or a headset is available too. */
@Composable
private fun RouteAction(route: AudioRoute, available: Set<AudioRoute>, onRoute: (AudioRoute) -> Unit, size: Dp, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    val several = available.count { it != AudioRoute.EARPIECE } > 1 || (AudioRoute.BLUETOOTH in available || AudioRoute.WIRED_HEADSET in available)
    val icon = when (route) {
        AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
        AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
        AudioRoute.WIRED_HEADSET -> Icons.Filled.Headset
        AudioRoute.EARPIECE -> Icons.Filled.PhoneInTalk
    }
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // A plain button for TalkBack (it announces the current route); it looks "on" when the sound is not in the earpiece.
        CallActionButton(
            icon = icon,
            label = stringResource(R.string.incall_audio),
            onClick = { if (several) menu = true else onRoute(if (route == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER) },
            active = route != AudioRoute.EARPIECE,
            stateDescription = stringResource(routeName(route)),
            size = size
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            available.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(routeName(option))) },
                    onClick = {
                        menu = false
                        onRoute(option)
                    }
                )
            }
        }
    }
}

private fun routeName(route: AudioRoute): Int = when (route) {
    AudioRoute.EARPIECE -> R.string.incall_route_earpiece
    AudioRoute.SPEAKER -> R.string.incall_route_speaker
    AudioRoute.BLUETOOTH -> R.string.incall_route_bluetooth
    AudioRoute.WIRED_HEADSET -> R.string.incall_route_headset
}

@Composable
private fun DtmfKeypad(onDown: (Char) -> Unit, onUp: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small), modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
        DIAL_KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), modifier = Modifier.fillMaxWidth()) {
                row.forEach { char -> DtmfKey(char, onDown, onUp, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DtmfKey(char: Char, onDown: (Char) -> Unit, onUp: () -> Unit, modifier: Modifier) {
    val keyName = when (char) {
        '*' -> stringResource(R.string.incall_key_star)
        '#' -> stringResource(R.string.incall_key_pound)
        else -> char.toString()
    }
    var pressed by remember { mutableStateOf(false) }
    val fill by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = motionEffectsSpec(),
        label = "dtmfFill"
    )
    Surface(
        shape = CircleShape,
        color = fill,
        modifier = modifier.heightIn(min = MIN_TOUCH_TARGET).pointerInput(char) {
            detectTapGestures(onPress = {
                pressed = true
                onDown(char)
                tryAwaitRelease()
                pressed = false
                onUp()
            })
        }.semantics(mergeDescendants = true) {
            // The touch handler above cannot be reached by TalkBack; this gives it a button that sends the tone.
            role = Role.Button
            contentDescription = keyName
            onClick {
                onDown(char)
                onUp()
                true
            }
        }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).clearAndSetSemantics {}) {
            Text(char.toString(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
