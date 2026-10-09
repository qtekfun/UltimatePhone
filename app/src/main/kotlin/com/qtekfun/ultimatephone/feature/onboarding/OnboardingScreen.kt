package com.qtekfun.ultimatephone.feature.onboarding

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.StatusChip
import com.qtekfun.ultimatephone.core.designsystem.motionEffectsSpec

/**
 * The first-run flow: welcome, roles, permissions, data regions, Nextcloud, battery guide and summary. Every step but the
 * first and the last can be skipped, and nothing needs an account or a network.
 *
 * @param embedded true when shown inside the app's navigation (re-run from Settings), where the system bars are already handled.
 */
@Composable
fun OnboardingRoute(onFinished: () -> Unit, embedded: Boolean = false, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val flow = state.flow
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshRoles()
        viewModel.refreshNetwork()
    }
    Scaffold(
        contentWindowInsets = if (embedded) WindowInsets(0) else WindowInsets.safeDrawing,
        bottomBar = {
            NavigationButtons(
                modifier = if (embedded) {
                    Modifier
                } else {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                },
                state = flow,
                onBack = viewModel::back,
                onNext = viewModel::next,
                onSkip = viewModel::skip
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Stepper(position = flow.position, total = flow.total)
            Text(
                stringResource(R.string.onboarding_step_of, flow.position, flow.total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Small).semantics { liveRegion = LiveRegionMode.Polite }
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                StepIcon(flow.step)
                when (flow.step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.ROLES -> RolesStep(state.roles, viewModel)
                    OnboardingStep.PERMISSIONS -> PermissionsStep()
                    OnboardingStep.DATA -> DataStep(state, viewModel)
                    OnboardingStep.NEXTCLOUD -> NextcloudStep(
                        formOpen = flow.nextcloudFormOpen,
                        onOpenForm = viewModel::openNextcloudForm,
                        onCloseForm = viewModel::closeNextcloudForm,
                        onConnected = viewModel::next,
                        onLater = viewModel::skip
                    )
                    OnboardingStep.BATTERY -> BatteryStep(state)
                    OnboardingStep.SUMMARY -> SummaryStep(state, onFinish = { download -> viewModel.finish(download, onFinished) })
                }
            }
        }
    }
}

/**
 * Progress as a row of segments, one per step: those reached are filled. The text under it says the same as the
 * stepper, so the stepper is hidden from TalkBack.
 */
@Composable
private fun Stepper(position: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Large, vertical = Spacing.Small).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)
    ) {
        repeat(total) { index ->
            val reached = index < position
            val color by animateColorAsState(
                targetValue = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                animationSpec = motionEffectsSpec(),
                label = "stepSegment"
            )
            Box(modifier = Modifier.weight(1f).height(STEP_SEGMENT_HEIGHT).clip(CircleShape).background(color))
        }
    }
}

private val STEP_SEGMENT_HEIGHT = 6.dp

/** The illustration of a step: a big icon in a tonal circle, decorative (the step title says the same). */
@Composable
private fun StepIcon(step: OnboardingStep) {
    Box(
        modifier = Modifier.size(STEP_ICON_CIRCLE).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            stepIcon(step),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(STEP_ICON_SIZE)
        )
    }
}

private val STEP_ICON_CIRCLE = 88.dp
private val STEP_ICON_SIZE = 44.dp

private fun stepIcon(step: OnboardingStep): ImageVector = when (step) {
    OnboardingStep.WELCOME -> Icons.Filled.Call
    OnboardingStep.ROLES -> Icons.Filled.AdminPanelSettings
    OnboardingStep.PERMISSIONS -> Icons.Filled.VerifiedUser
    OnboardingStep.DATA -> Icons.Filled.Public
    OnboardingStep.NEXTCLOUD -> Icons.Filled.Cloud
    OnboardingStep.BATTERY -> Icons.Filled.BatteryChargingFull
    OnboardingStep.SUMMARY -> Icons.Filled.CheckCircle
}

@Composable
private fun NavigationButtons(modifier: Modifier, state: OnboardingState, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    // The summary has its own finishing buttons.
    if (state.isLast) {
        if (!state.isFirst) {
            TextButton(onClick = onBack, modifier = modifier.padding(horizontal = Spacing.Medium)) {
                Text(stringResource(R.string.onboarding_back))
            }
        }
        return
    }
    val nextLabel = stringResource(if (state.isFirst) R.string.onboarding_start else R.string.onboarding_next)
    val nextButtonModifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
    if (LocalDensity.current.fontScale >= STACKED_FONT_SCALE) {
        // Large text: the buttons would not fit side by side, so they stack with the main action first.
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)
        ) {
            Button(onClick = onNext, modifier = nextButtonModifier.fillMaxWidth()) { Text(nextLabel) }
            if (state.step.skippable) TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_skip)) }
            if (!state.isFirst) TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_back)) }
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!state.isFirst) TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_back)) }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (state.step.skippable) TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
            Button(onClick = onNext, modifier = nextButtonModifier) { Text(nextLabel) }
        }
    }
}

private val MIN_TOUCH_TARGET = 56.dp
private const val STACKED_FONT_SCALE = 1.3f

@Composable
internal fun StepTitle(title: Int, body: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The inner padding of a card-like [SettingsGroup] that holds free-form content instead of rows. */
internal val CardContentPadding = Modifier.padding(Spacing.Medium)

@Composable
private fun WelcomeStep() {
    StepTitle(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body)
    SettingsGroup {
        // Each point is one item for TalkBack; the icon is decoration.
        SettingsRow(title = stringResource(R.string.onboarding_welcome_point_private), icon = Icons.Filled.Lock)
        SettingsRow(title = stringResource(R.string.onboarding_welcome_point_offline), icon = Icons.Filled.CloudOff)
        SettingsRow(title = stringResource(R.string.onboarding_welcome_point_optional), icon = Icons.Filled.Tune)
    }
}

// ---- Roles ----------------------------------------------------------------------------------------------------------

@Composable
private fun RolesStep(roles: RolesState, viewModel: OnboardingViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshRoles() }
    StepTitle(R.string.onboarding_roles_title, R.string.onboarding_roles_body)
    RoleCard(
        title = R.string.onboarding_role_phone_title,
        body = R.string.onboarding_role_phone_body,
        available = roles.dialerAvailable,
        held = roles.dialerHeld,
        onRequest = { viewModel.dialerRoleIntent()?.let(launcher::launch) }
    )
    RoleCard(
        title = R.string.onboarding_role_screening_title,
        body = R.string.onboarding_role_screening_body,
        available = roles.screeningAvailable,
        held = roles.screeningHeld,
        onRequest = { viewModel.screeningRoleIntent()?.let(launcher::launch) }
    )
}

/** A titled explanation with its state: a green "Active" chip, "not available", or the button that asks for it. */
@Composable
internal fun StepCard(title: Int, body: String, content: @Composable () -> Unit) {
    SettingsGroup {
        Column(modifier = CardContentPadding, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun RoleCard(title: Int, body: Int, available: Boolean, held: Boolean, onRequest: () -> Unit) {
    StepCard(title, stringResource(body)) {
        when {
            held -> StatusChip(stringResource(R.string.onboarding_role_active), icon = Icons.Filled.CheckCircle, kind = BannerKind.Success)
            !available -> StatusChip(stringResource(R.string.onboarding_role_unavailable))
            else -> FilledTonalButton(onClick = onRequest, modifier = Modifier.heightIn(min = Spacing.MinTarget)) {
                Text(stringResource(R.string.onboarding_role_request))
            }
        }
    }
}

// ---- Permissions ----------------------------------------------------------------------------------------------------

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var asked by remember { mutableStateOf(emptySet<PermissionGroup>()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val granted: (String) -> Boolean = { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    StepTitle(R.string.onboarding_perm_title, R.string.onboarding_perm_body)
    // Re-read after every answer and every return from system settings.
    val missingByGroup = remember(tick) { PermissionGroup.entries.associateWith { it.missing(granted) } }
    PermissionGroup.entries.forEach { group ->
        val missing = missingByGroup.getValue(group)
        StepCard(group.title, stringResource(group.reason)) {
            when {
                missing.isEmpty() -> StatusChip(stringResource(R.string.onboarding_perm_granted), icon = Icons.Filled.CheckCircle, kind = BannerKind.Success)
                group in asked -> {
                    Text(stringResource(R.string.onboarding_perm_blocked), style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(
                        onClick = { context.startActivity(appDetailsIntent(context.packageName)) },
                        modifier = Modifier.heightIn(min = Spacing.MinTarget)
                    ) { Text(stringResource(R.string.onboarding_perm_open_settings)) }
                }
                else -> FilledTonalButton(
                    onClick = {
                        asked = asked + group
                        launcher.launch(missing.toTypedArray())
                    },
                    modifier = Modifier.heightIn(min = Spacing.MinTarget)
                ) { Text(stringResource(R.string.onboarding_perm_allow)) }
            }
        }
    }
}

private fun appDetailsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
