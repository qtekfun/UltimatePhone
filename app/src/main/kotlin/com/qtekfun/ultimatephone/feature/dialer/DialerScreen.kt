package com.qtekfun.ultimatephone.feature.dialer

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.Avatar
import com.qtekfun.ultimatephone.core.designsystem.callColors
import com.qtekfun.ultimatephone.data.BusinessCategories
import com.qtekfun.ultimatephone.data.BusinessHit
import com.qtekfun.ultimatephone.feature.data.BusinessAvatar
import com.qtekfun.ultimatephone.feature.data.BusinessCategoryLine

private data class Key(val char: Char, val letters: String = "")

private val KEYS = listOf(
    listOf(Key('1'), Key('2', "ABC"), Key('3', "DEF")),
    listOf(Key('4', "GHI"), Key('5', "JKL"), Key('6', "MNO")),
    listOf(Key('7', "PQRS"), Key('8', "TUV"), Key('9', "WXYZ")),
    listOf(Key('*'), Key('0', "+"), Key('#'))
)

@Composable
fun DialerScreen(initialNumber: String?, onRequestPhoneRole: () -> Unit, viewModel: DialerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val tones = rememberDtmfTones()

    LaunchedEffect(initialNumber) { if (!initialNumber.isNullOrBlank()) viewModel.setNumber(initialNumber) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.refresh()
        if (result[Manifest.permission.CALL_PHONE] == true) viewModel.call()
    }

    fun placeCall(action: () -> Boolean) {
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (canCall) {
            action()
        } else {
            permissions.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CONTACTS))
        }
    }

    val onKey: (Char) -> Unit = { char ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tones.play(char)
        viewModel.press(char)
    }
    val onLongZero = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.pressPlus()
    }
    val suggestions: @Composable (Modifier) -> Unit = { modifier ->
        Suggestions(
            suggestions = state.suggestions,
            businesses = state.businessSuggestions,
            modifier = modifier,
            onClick = { suggestion -> placeCall { viewModel.callSuggestion(suggestion) } },
            onBusinessClick = { hit -> placeCall { viewModel.callBusiness(hit) } }
        )
    }
    val numberAndSims: @Composable () -> Unit = {
        NumberField(
            text = state.displayNumber,
            onBackspace = { viewModel.backspace() },
            onClear = { viewModel.clear() },
            onPaste = { text -> viewModel.paste(text) }
        )
        if (state.sims.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                state.sims.forEach { sim ->
                    FilterChip(
                        selected = sim.key == state.selectedSimKey,
                        onClick = { viewModel.selectSim(sim.key) },
                        label = { Text(simLabel(sim.slot, sim.label)) }
                    )
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The keypad and the call button never give way: the suggestions are what shrinks (down to nothing) on a short screen.
        val landscape = maxWidth > maxHeight
        val compact = maxHeight < COMPACT_HEIGHT
        val keyHeight = if (compact) 52.dp else 64.dp
        val callSize = if (compact) 64.dp else 72.dp
        if (landscape) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.needsDefaultPhoneApp) DefaultPhoneBanner(onRequestPhoneRole)
                    suggestions(Modifier.weight(1f).fillMaxWidth())
                    numberAndSims()
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Keypad(keyHeight = 48.dp, onKey = onKey, onLongZero = onLongZero)
                    CallButton(size = callSize) { placeCall { viewModel.call() } }
                }
            }
        } else {
            // With very large text the whole screen scrolls instead of squeezing the keypad.
            val bigText = LocalDensity.current.fontScale >= BIG_FONT_SCALE
            Column(
                modifier = Modifier.fillMaxSize().then(if (bigText) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.needsDefaultPhoneApp) DefaultPhoneBanner(onRequestPhoneRole)
                suggestions(if (bigText) Modifier.fillMaxWidth().heightIn(max = 120.dp) else Modifier.weight(1f).fillMaxWidth())
                numberAndSims()
                Keypad(keyHeight = keyHeight, onKey = onKey, onLongZero = onLongZero)
                CallButton(size = callSize) { placeCall { viewModel.call() } }
            }
        }
    }
}

@Composable
private fun CallButton(size: Dp, onClick: () -> Unit) {
    val colors = callColors()
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 8.dp).size(width = size * 2, height = size),
        shape = RoundedCornerShape(size / 2),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.answer, contentColor = colors.onAnswer)
    ) {
        Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.dialer_call), modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun simLabel(slot: Int, label: String): String = if (slot >= 0) stringResource(R.string.dialer_sim_slot, slot + 1) else label

@Composable
private fun DefaultPhoneBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.phone_role_banner_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.phone_role_banner_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(stringResource(R.string.phone_role_banner_action)) }
        }
    }
}

@Composable
private fun Suggestions(
    suggestions: List<com.qtekfun.ultimatephone.core.contacts.DialerSuggestion>,
    businesses: List<BusinessHit>,
    modifier: Modifier,
    onClick: (com.qtekfun.ultimatephone.core.contacts.DialerSuggestion) -> Unit,
    onBusinessClick: (BusinessHit) -> Unit
) {
    LazyColumn(modifier = modifier, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(suggestions, key = { it.lookupKey + it.number }) { suggestion ->
            Row(
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onClick(suggestion) }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Avatar(name = suggestion.displayName)
                Column {
                    Text(suggestion.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(suggestion.number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(businesses, key = { "business:" + it.e164 }) { hit ->
            Row(
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onBusinessClick(hit) }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BusinessAvatar(BusinessCategories.iconName(hit.category))
                Column {
                    Text(hit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BusinessCategoryLine(BusinessCategories.normalized(hit.category), BusinessCategories.iconName(hit.category))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberField(text: String, onBackspace: () -> Unit, onClear: () -> Unit, onPaste: (String) -> Unit) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = {
                val clip = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!clip.isNullOrBlank()) onPaste(clip)
            }),
            contentAlignment = Alignment.Center
        ) {
            // Long numbers shrink, and if they still do not fit the start is cut, so the last digits stay visible.
            Text(
                text = text,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = numberFontSizeSp(text.length).sp),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = text }
            )
        }
        if (text.isNotEmpty()) {
            Box(modifier = Modifier.combinedClickable(onClick = onBackspace, onLongClick = onClear)) {
                IconButton(onClick = onBackspace) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.dialer_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(keyHeight: Dp, onKey: (Char) -> Unit, onLongZero: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).heightIn(min = keyHeight).combinedClickable(
                            role = Role.Button,
                            onClick = { onKey(key.char) },
                            onLongClick = { if (key.char == '0') onLongZero() else onKey(key.char) }
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(key.char.toString(), style = MaterialTheme.typography.headlineSmall)
                            if (key.letters.isNotEmpty()) Text(key.letters, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

/** Plays the DTMF tone of a key press, unless the system "dial pad tones" setting is off. */
private class DtmfTones(private val generator: ToneGenerator?, private val enabled: () -> Boolean) {
    fun play(char: Char) {
        if (!enabled()) return
        val tone = when (char) {
            in '0'..'9' -> char - '0'
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        generator?.startTone(tone, TONE_MS)
    }

    private companion object {
        const val TONE_MS = 120
    }
}

@Composable
private fun rememberDtmfTones(): DtmfTones {
    val context = LocalContext.current
    val generator = remember { runCatching { ToneGenerator(AudioManager.STREAM_DTMF, TONE_VOLUME) }.getOrNull() }
    DisposableEffect(generator) { onDispose { generator?.release() } }
    return remember(generator) {
        DtmfTones(generator) { Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1 }
    }
}

private const val TONE_VOLUME = 80
private val COMPACT_HEIGHT = 640.dp
private const val BIG_FONT_SCALE = 1.5f
