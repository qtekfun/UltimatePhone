package com.qtekfun.ultimatephone.incall

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.Avatar
import com.qtekfun.ultimatephone.core.designsystem.callColors
import com.qtekfun.ultimatephone.core.telecom.AudioRoute
import com.qtekfun.ultimatephone.core.telecom.CallDurationFormatter
import com.qtekfun.ultimatephone.core.telecom.CallInfo
import com.qtekfun.ultimatephone.core.telecom.CallStatus
import com.qtekfun.ultimatephone.feature.data.BusinessAvatar
import com.qtekfun.ultimatephone.feature.data.BusinessCategoryLine
import com.qtekfun.ultimatephone.feature.spam.CallSpamPanel
import com.qtekfun.ultimatephone.feature.spam.CallSpamUi
import kotlinx.coroutines.delay

private const val ENDED_DELAY_MS = 1500L
private const val MILLIS_PER_SECOND = 1000L
private val DIAL_KEYS = listOf("123", "456", "789", "*0#")

/** Below this height the screen switches to smaller avatar and controls. */
private val COMPACT_HEIGHT = 600.dp
private val MIN_TOUCH_TARGET = 56.dp
private val HANGUP_MAX_WIDTH = 320.dp

/** What the layout needs to know about the space it has. */
private data class CallLayout(val landscape: Boolean, val compact: Boolean) {
    val avatarSize: Dp get() = if (compact) 64.dp else 96.dp
    val actionSize: Dp get() = if (compact) 56.dp else 64.dp
    val answerSize: Dp get() = if (compact) 72.dp else 88.dp
    val hangupHeight: Dp get() = if (compact) 64.dp else 72.dp
}

@Composable
fun InCallScreen(viewModel: InCallViewModel, onAddCall: () -> Unit, onFinished: () -> Unit) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val audio by viewModel.audio.collectAsStateWithLifecycle()
    val spam by viewModel.spam.collectAsStateWithLifecycle()
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
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (primary == null) {
                Text(
                    stringResource(R.string.incall_call_ended),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
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
                            onDtmfDown = { viewModel.dtmfDown(primary, it) },
                            onDtmfUp = { viewModel.dtmfUp(primary) }
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
                                onHangup = { viewModel.hangup(primary) }
                            )
                        }
                    }
                    if (layout.landscape) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            info(Modifier.weight(1f).fillMaxHeight())
                            Spacer(Modifier.width(16.dp))
                            controls(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            info(Modifier.weight(1f).fillMaxWidth())
                            controls(Modifier.fillMaxWidth().padding(bottom = 16.dp))
                        }
                    }
                }
            }
        }
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
    onDtmfDown: (Char) -> Unit,
    onDtmfUp: () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (other != null) OtherCallCard(other, onSwap = onSwap)
        CallHeader(call, layout, showAvatar = !showKeypad)
        CallSpamPanel(ui = spam, ringing = call.status == CallStatus.RINGING, onNotSpam = onNotSpam, onMarkSpam = onMarkSpam)
        if (showKeypad) {
            Spacer(Modifier.size(16.dp))
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = if (layout.compact) 8.dp else 24.dp)) {
        if (showAvatar) {
            if (call.isBusiness) BusinessAvatar(call.businessIcon, size = layout.avatarSize) else Avatar(name = call.title, size = layout.avatarSize)
        }
        // The name is the most important line: large, up to two lines.
        Text(
            call.title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (call.isBusiness) BusinessCategoryLine(call.businessCategory, call.businessIcon)
        if (call.contactName != null && call.displayNumber.isNotEmpty()) {
            Text(call.displayNumber, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        call.sim?.let { sim ->
            val label = if (sim.slot >= 0) stringResource(R.string.dialer_sim_slot, sim.slot + 1) else sim.label
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val status = when (call.status) {
            CallStatus.RINGING -> stringResource(R.string.incall_incoming)
            CallStatus.DIALING, CallStatus.CONNECTING -> stringResource(R.string.incall_calling)
            CallStatus.ACTIVE -> CallDurationFormatter.format(elapsed)
            CallStatus.HOLDING -> stringResource(R.string.incall_on_hold, CallDurationFormatter.format(elapsed))
            CallStatus.DISCONNECTING, CallStatus.DISCONNECTED -> call.disconnectLabel ?: stringResource(R.string.incall_call_ended)
            CallStatus.OTHER -> ""
        }
        Text(
            status,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (call.isConference) {
            Text(stringResource(R.string.incall_conference, call.participants), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OtherCallCard(call: CallInfo, onSwap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(if (call.status == CallStatus.RINGING) R.string.incall_waiting else R.string.incall_held, call.title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            TextButton(onClick = onSwap, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) { Text(stringResource(R.string.incall_swap)) }
        }
    }
}

@Composable
private fun RingingControls(layout: CallLayout, modifier: Modifier, onAnswer: () -> Unit, onReject: () -> Unit) {
    val colors = callColors()
    Row(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        RoundAction(Icons.Filled.CallEnd, stringResource(R.string.incall_decline), colors.decline, colors.onDecline, layout.answerSize, onReject)
        RoundAction(Icons.Filled.Call, stringResource(R.string.incall_answer), colors.answer, colors.onAnswer, layout.answerSize, onAnswer)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, container: Color, content: Color, size: Dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(size),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content)
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(size / 2))
        }
        // The icon already carries the description for TalkBack.
        Text(description, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp).clearAndSetSemantics {})
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
    onHangup: () -> Unit
) {
    val size = layout.actionSize
    val holding = call.status == CallStatus.HOLDING
    // Hold and merge only appear when they can do something.
    val actions = buildList {
        add(
            CallAction("mute") {
                ToggleAction(
                    if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    stringResource(R.string.incall_mute),
                    muted,
                    onMute,
                    size = size,
                    modifier = Modifier.weight(1f)
                )
            }
        )
        add(
            CallAction("keypad") {
                ToggleAction(Icons.Filled.Dialpad, stringResource(R.string.incall_keypad), keypadShown, onKeypad, size = size, modifier = Modifier.weight(1f))
            }
        )
        add(CallAction("audio") { RouteAction(route, availableRoutes, onRoute, size, Modifier.weight(1f)) })
        if (call.canHold || holding) {
            add(
                CallAction("hold") {
                    ToggleAction(
                        if (holding) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        stringResource(R.string.incall_hold),
                        holding,
                        onHold,
                        size = size,
                        modifier = Modifier.weight(1f)
                    )
                }
            )
        }
        add(
            CallAction("add") {
                ToggleAction(Icons.Filled.PersonAdd, stringResource(R.string.incall_add_call), false, onAddCall, size = size, modifier = Modifier.weight(1f))
            }
        )
        if (hasOtherCall || call.canMerge || call.isConference) {
            add(
                CallAction("merge") {
                    ToggleAction(
                        if (call.isConference) Icons.AutoMirrored.Filled.CallSplit else Icons.AutoMirrored.Filled.CallMerge,
                        stringResource(R.string.incall_merge),
                        call.isConference,
                        onMerge,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.chunked(GRID_COLUMNS).forEach { rowActions ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    rowActions.forEach { action -> action.content(this) }
                    repeat(GRID_COLUMNS - rowActions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        HangupButton(height = layout.hangupHeight, onClick = onHangup)
    }
}

private const val GRID_COLUMNS = 3

/** The most important control: large, red, centred, at the bottom where the thumb rests. */
@Composable
private fun HangupButton(height: Dp, onClick: () -> Unit) {
    val colors = callColors()
    Button(
        onClick = onClick,
        modifier = Modifier.padding(top = 16.dp).fillMaxWidth().widthIn(max = HANGUP_MAX_WIDTH).heightIn(min = height),
        shape = RoundedCornerShape(height / 2),
        colors = ButtonDefaults.buttonColors(containerColor = colors.decline, contentColor = colors.onDecline)
    ) {
        Icon(Icons.Filled.CallEnd, contentDescription = null, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.incall_end_call), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ToggleAction(
    icon: ImageVector,
    description: String,
    checked: Boolean,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        FilledTonalIconToggleButton(checked = checked, onCheckedChange = { onClick() }, enabled = enabled, modifier = Modifier.size(size)) {
            Icon(icon, contentDescription = description)
        }
        // The icon already carries the description for TalkBack.
        Text(
            description,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics {}
        )
    }
}

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
        ToggleAction(
            icon = icon,
            description = stringResource(R.string.incall_audio),
            checked = route != AudioRoute.EARPIECE,
            onClick = { if (several) menu = true else onRoute(if (route == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER) },
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
        DIAL_KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { char ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET).pointerInput(char) {
                            detectTapGestures(onPress = {
                                onDown(char)
                                tryAwaitRelease()
                                onUp()
                            })
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                            Text(char.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}
