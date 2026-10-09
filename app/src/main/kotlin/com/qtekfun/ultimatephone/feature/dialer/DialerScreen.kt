package com.qtekfun.ultimatephone.feature.dialer

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import com.qtekfun.ultimatephone.core.designsystem.AvatarStyled
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.CallButtonKind
import com.qtekfun.ultimatephone.core.designsystem.CallPillButton
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.SegmentedChoice
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.StatusChip
import com.qtekfun.ultimatephone.core.designsystem.UiAction
import com.qtekfun.ultimatephone.core.designsystem.motionEffectsSpec
import com.qtekfun.ultimatephone.core.designsystem.pressScale
import com.qtekfun.ultimatephone.core.designsystem.tabularFigures
import com.qtekfun.ultimatephone.data.BusinessCategories
import com.qtekfun.ultimatephone.data.BusinessHit
import com.qtekfun.ultimatephone.feature.data.businessCategoryLabel
import com.qtekfun.ultimatephone.feature.data.businessIcon

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
            // One segment per SIM: the one used for the next call is filled and checked.
            SegmentedChoice(
                options = state.sims,
                selected = state.sims.firstOrNull { it.key == state.selectedSimKey },
                onSelected = { sim -> viewModel.selectSim(sim.key) },
                label = { sim -> simLabel(sim.slot, sim.label) },
                modifier = Modifier.padding(vertical = Spacing.Small)
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The keypad and the call button never give way: the suggestions are what shrinks (down to nothing) on a short screen.
        val landscape = maxWidth > maxHeight
        val compact = maxHeight < COMPACT_HEIGHT
        val keyHeight = if (compact) 52.dp else 68.dp
        val callSize = if (compact) 64.dp else 76.dp
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
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
                    DialCallButton(size = callSize) { placeCall { viewModel.call() } }
                }
            }
        } else {
            // With very large text the whole screen scrolls instead of squeezing the keypad.
            val bigText = LocalDensity.current.fontScale >= BIG_FONT_SCALE
            Column(
                modifier = Modifier.fillMaxSize().then(if (bigText) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = Spacing.Medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.needsDefaultPhoneApp) DefaultPhoneBanner(onRequestPhoneRole)
                suggestions(if (bigText) Modifier.fillMaxWidth().heightIn(max = 120.dp) else Modifier.weight(1f).fillMaxWidth())
                numberAndSims()
                Keypad(keyHeight = keyHeight, onKey = onKey, onLongZero = onLongZero)
                DialCallButton(size = callSize) { placeCall { viewModel.call() } }
            }
        }
    }
}

/** The big green call pill under the keypad. */
@Composable
private fun DialCallButton(size: Dp, onClick: () -> Unit) {
    CallPillButton(
        kind = CallButtonKind.Answer,
        label = stringResource(R.string.dialer_call),
        onClick = onClick,
        modifier = Modifier.padding(vertical = Spacing.Small).width(size * 2.6f),
        height = size,
        showLabel = false
    )
}

@Composable
private fun simLabel(slot: Int, label: String): String = if (slot >= 0) stringResource(R.string.dialer_sim_slot, slot + 1) else label

@Composable
private fun DefaultPhoneBanner(onClick: () -> Unit) {
    InfoBanner(
        kind = BannerKind.Info,
        title = stringResource(R.string.phone_role_banner_title),
        body = stringResource(R.string.phone_role_banner_body),
        action = UiAction(stringResource(R.string.phone_role_banner_action), onClick),
        modifier = Modifier.padding(top = Spacing.Small)
    )
}

@Composable
private fun Suggestions(
    suggestions: List<com.qtekfun.ultimatephone.core.contacts.DialerSuggestion>,
    businesses: List<BusinessHit>,
    modifier: Modifier,
    onClick: (com.qtekfun.ultimatephone.core.contacts.DialerSuggestion) -> Unit,
    onBusinessClick: (BusinessHit) -> Unit
) {
    val callLabel = stringResource(R.string.dialer_call)
    LazyColumn(modifier = modifier, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        items(suggestions, key = { it.lookupKey + it.number }) { suggestion ->
            SuggestionCard(callLabel = callLabel, onClick = { onClick(suggestion) }) {
                AvatarStyled(name = suggestion.displayName, size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(suggestion.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        suggestion.number,
                        style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(businesses, key = { "business:" + it.e164 }) { hit ->
            val iconName = BusinessCategories.iconName(hit.category)
            SuggestionCard(callLabel = callLabel, onClick = { onBusinessClick(hit) }) {
                AvatarStyled(name = null, size = 44.dp, business = true, businessIcon = businessIcon(iconName))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                    Text(hit.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    StatusChip(
                        text = stringResource(businessCategoryLabel(BusinessCategories.normalized(hit.category))),
                        icon = businessIcon(iconName)
                    )
                }
            }
        }
    }
}

/** One suggestion: a tonal card that is a single "Call" button (at least 48 dp, whole card). */
@Composable
private fun SuggestionCard(callLabel: String, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TARGET)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = callLabel, onClick = onClick)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberField(text: String, onBackspace: () -> Unit, onClear: () -> Unit, onPaste: (String) -> Unit) {
    val context = LocalContext.current
    // A pill that holds the number and the delete button; empty until a digit is typed.
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Small).heightIn(min = 72.dp)
            .clip(RoundedCornerShape(36.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = Spacing.Large, end = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f).combinedClickable(onClick = {}, onLongClickLabel = stringResource(R.string.dialer_paste), onLongClick = {
                val clip = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!clip.isNullOrBlank()) onPaste(clip)
            }),
            contentAlignment = Alignment.Center
        ) {
            // Long numbers shrink, and if they still do not fit the start is cut, so the last digits stay visible.
            Text(
                text = text,
                style = MaterialTheme.typography.displaySmall.tabularFigures().copy(fontSize = (numberFontSizeSp(text.length) * NUMBER_SCALE).sp),
                // Two lines with very large text, so a long number is not cut to a few digits.
                maxLines = if (LocalDensity.current.fontScale >= BIG_FONT_SCALE) 2 else 1,
                overflow = TextOverflow.StartEllipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = text }
            )
        }
        AnimatedVisibility(visible = text.isNotEmpty()) {
            // One control: tap deletes a digit, long press clears the number. TalkBack offers both as actions.
            Box(
                modifier = Modifier.minimumInteractiveComponentSize().clip(CircleShape).combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.dialer_delete),
                    onClick = onBackspace,
                    onLongClickLabel = stringResource(R.string.dialer_clear),
                    onLongClick = onClear
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.dialer_delete))
            }
        }
    }
}

@Composable
private fun Keypad(keyHeight: Dp, onKey: (Char) -> Unit, onLongZero: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small + Spacing.XSmall)) {
                row.forEach { key -> DialKey(key, keyHeight, onKey, onLongZero, Modifier.weight(1f)) }
            }
        }
    }
}

/** A rounded-square key: a subtle tonal fill that turns into the primary container while pressed, with a spring press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(key: Key, keyHeight: Dp, onKey: (Char) -> Unit, onLongZero: () -> Unit, modifier: Modifier) {
    // "2 A B C" would be read letter by letter: say the digit (or the symbol's name) only.
    val keyName = when (key.char) {
        '*' -> stringResource(R.string.incall_key_star)
        '#' -> stringResource(R.string.incall_key_pound)
        else -> key.char.toString()
    }
    val plusLabel = if (key.char == '0') stringResource(R.string.dialer_key_plus) else null
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = motionEffectsSpec(),
        label = "keyFill"
    )
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = fill,
        modifier = modifier.heightIn(min = keyHeight).pressScale(interaction).semantics { contentDescription = keyName }.combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            onLongClickLabel = plusLabel,
            onClick = { onKey(key.char) },
            onLongClick = { if (key.char == '0') onLongZero() else onKey(key.char) }
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.clearAndSetSemantics {}
        ) {
            Text(key.char.toString(), style = MaterialTheme.typography.headlineMedium)
            if (key.letters.isNotEmpty()) {
                Text(
                    key.letters,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = KEY_LETTER_SPACING),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private val MIN_TARGET = 48.dp
private val COMPACT_HEIGHT = 640.dp
private const val BIG_FONT_SCALE = 1.5f

private const val NUMBER_SCALE = 1.15f
private val KEY_LETTER_SPACING = 1.5.sp
