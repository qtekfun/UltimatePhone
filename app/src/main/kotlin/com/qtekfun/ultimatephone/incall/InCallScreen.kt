package com.qtekfun.ultimatephone.incall

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.Avatar
import com.qtekfun.ultimatephone.core.telecom.AudioRoute
import com.qtekfun.ultimatephone.core.telecom.CallDurationFormatter
import com.qtekfun.ultimatephone.core.telecom.CallInfo
import com.qtekfun.ultimatephone.core.telecom.CallStatus
import com.qtekfun.ultimatephone.feature.data.BusinessAvatar
import com.qtekfun.ultimatephone.feature.data.BusinessCategoryLine
import kotlinx.coroutines.delay

private const val ENDED_DELAY_MS = 1500L
private val RED = Color(0xFFC62828)
private val GREEN = Color(0xFF2E7D32)
private val DIAL_KEYS = listOf("123", "456", "789", "*0#")

@Composable
fun InCallScreen(viewModel: InCallViewModel, onAddCall: () -> Unit, onFinished: () -> Unit) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val audio by viewModel.audio.collectAsStateWithLifecycle()
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (primary == null) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(stringResource(R.string.incall_call_ended), style = MaterialTheme.typography.headlineSmall)
            }
            return@Surface
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (other != null) OtherCallCard(other, onSwap = viewModel::swap)
            CallHeader(primary)
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showKeypad && primary.status != CallStatus.RINGING) {
                    DtmfKeypad(onDown = { viewModel.dtmfDown(primary, it) }, onUp = { viewModel.dtmfUp(primary) })
                }
            }
            if (primary.status == CallStatus.RINGING) {
                RingingControls(onAnswer = { viewModel.answer(primary) }, onReject = { viewModel.reject(primary) })
            } else {
                OngoingControls(
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
    }
}

@Composable
private fun CallHeader(call: CallInfo) {
    val elapsed by produceState(0L, call.status, call.connectedAtMillis) {
        while (call.connectedAtMillis > 0 && (call.status == CallStatus.ACTIVE || call.status == CallStatus.HOLDING)) {
            value = (System.currentTimeMillis() - call.connectedAtMillis) / MILLIS_PER_SECOND
            delay(MILLIS_PER_SECOND)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 32.dp)) {
        if (call.isBusiness) BusinessAvatar(call.businessIcon, size = 96.dp) else Avatar(name = call.title, size = 96.dp)
        Text(call.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        if (call.isBusiness) BusinessCategoryLine(call.businessCategory, call.businessIcon)
        if (call.contactName != null && call.displayNumber.isNotEmpty()) {
            Text(call.displayNumber, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val status = when (call.status) {
            CallStatus.RINGING -> stringResource(R.string.incall_incoming)
            CallStatus.DIALING, CallStatus.CONNECTING -> stringResource(R.string.incall_calling)
            CallStatus.ACTIVE -> CallDurationFormatter.format(elapsed)
            CallStatus.HOLDING -> stringResource(R.string.incall_on_hold, CallDurationFormatter.format(elapsed))
            CallStatus.DISCONNECTING, CallStatus.DISCONNECTED -> call.disconnectLabel ?: stringResource(R.string.incall_call_ended)
            CallStatus.OTHER -> ""
        }
        Text(status, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        call.sim?.let { sim ->
            val label = if (sim.slot >= 0) stringResource(R.string.dialer_sim_slot, sim.slot + 1) else sim.label
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (call.isConference) {
            Text(stringResource(R.string.incall_conference, call.participants), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OtherCallCard(call: CallInfo, onSwap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(if (call.status == CallStatus.RINGING) R.string.incall_waiting else R.string.incall_held, call.title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSwap) { Text(stringResource(R.string.incall_swap)) }
        }
    }
}

@Composable
private fun RingingControls(onAnswer: () -> Unit, onReject: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundAction(Icons.Filled.CallEnd, stringResource(R.string.incall_decline), RED, onReject)
        RoundAction(Icons.Filled.Call, stringResource(R.string.incall_answer), GREEN, onAnswer)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(80.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White)
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(36.dp))
        }
        Text(description, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun OngoingControls(
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleAction(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, stringResource(R.string.incall_mute), muted, onMute)
            ToggleAction(Icons.Filled.Dialpad, stringResource(R.string.incall_keypad), keypadShown, onKeypad)
            RouteAction(route, availableRoutes, onRoute)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleAction(
                if (call.status == CallStatus.HOLDING) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                stringResource(R.string.incall_hold),
                call.status == CallStatus.HOLDING,
                onHold,
                enabled = call.canHold
            )
            ToggleAction(Icons.Filled.PersonAdd, stringResource(R.string.incall_add_call), false, onAddCall)
            ToggleAction(
                if (call.isConference) Icons.AutoMirrored.Filled.CallSplit else Icons.AutoMirrored.Filled.CallMerge,
                stringResource(R.string.incall_merge),
                call.isConference,
                onMerge,
                enabled = hasOtherCall || call.canMerge
            )
        }
        FilledIconButton(
            onClick = onHangup,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).size(width = 120.dp, height = 72.dp),
            shape = RoundedCornerShape(36.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = RED, contentColor = Color.White)
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = stringResource(R.string.incall_end_call), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun ToggleAction(icon: ImageVector, description: String, checked: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconToggleButton(checked = checked, onCheckedChange = { onClick() }, enabled = enabled, modifier = Modifier.size(64.dp)) {
            Icon(icon, contentDescription = description)
        }
        Text(description, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** One button: toggles the speaker, or opens a menu when Bluetooth or a headset is available too. */
@Composable
private fun RouteAction(route: AudioRoute, available: Set<AudioRoute>, onRoute: (AudioRoute) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val several = available.count { it != AudioRoute.EARPIECE } > 1 || (AudioRoute.BLUETOOTH in available || AudioRoute.WIRED_HEADSET in available)
    val icon = when (route) {
        AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
        AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
        AudioRoute.WIRED_HEADSET -> Icons.Filled.Headset
        AudioRoute.EARPIECE -> Icons.Filled.PhoneInTalk
    }
    Box {
        ToggleAction(
            icon = icon,
            description = stringResource(R.string.incall_audio),
            checked = route != AudioRoute.EARPIECE,
            onClick = { if (several) menu = true else onRoute(if (route == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER) }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DIAL_KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { char ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(width = 80.dp, height = 56.dp).pointerInput(char) {
                            detectTapGestures(onPress = {
                                onDown(char)
                                tryAwaitRelease()
                                onUp()
                            })
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(56.dp)) {
                            Text(char.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

private const val MILLIS_PER_SECOND = 1000L
