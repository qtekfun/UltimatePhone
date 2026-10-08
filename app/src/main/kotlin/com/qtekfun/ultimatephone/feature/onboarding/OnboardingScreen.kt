package com.qtekfun.ultimatephone.feature.onboarding

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R

/**
 * The first-run flow: welcome, roles, permissions, data regions, Nextcloud, battery guide and summary. Every step but the
 * first and the last can be skipped, and nothing needs an account or a network.
 *
 * @param embedded true when shown inside the app's navigation (re-run from Settings), where the system bars are already handled.
 * @param onSetUpNextcloud hook for the Nextcloud step; null until that feature exists, which shows only "set up later".
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    embedded: Boolean = false,
    onSetUpNextcloud: (() -> Unit)? = null,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
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
            LinearProgressIndicator(progress = { flow.position.toFloat() / flow.total }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.onboarding_step_of, flow.position, flow.total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (flow.step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.ROLES -> RolesStep(state.roles, viewModel)
                    OnboardingStep.PERMISSIONS -> PermissionsStep()
                    OnboardingStep.DATA -> DataStep(state, viewModel)
                    OnboardingStep.NEXTCLOUD -> NextcloudStep(onSetUpNextcloud, onLater = viewModel::skip)
                    OnboardingStep.BATTERY -> BatteryStep(state)
                    OnboardingStep.SUMMARY -> SummaryStep(state, onFinish = { download -> viewModel.finish(download, onFinished) })
                }
            }
        }
    }
}

@Composable
private fun NavigationButtons(modifier: Modifier, state: OnboardingState, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    // The summary has its own finishing buttons.
    if (state.isLast) {
        if (!state.isFirst) TextButton(onClick = onBack, modifier = modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.onboarding_back)) }
        return
    }
    val nextLabel = stringResource(if (state.isFirst) R.string.onboarding_start else R.string.onboarding_next)
    val nextButtonModifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
    if (LocalDensity.current.fontScale >= STACKED_FONT_SCALE) {
        // Large text: the buttons would not fit side by side, so they stack with the main action first.
        Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onNext, modifier = nextButtonModifier.fillMaxWidth()) { Text(nextLabel) }
            if (state.step.skippable) TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_skip)) }
            if (!state.isFirst) TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_back)) }
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun WelcomeStep() {
    StepTitle(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body)
    listOf(R.string.onboarding_welcome_point_private, R.string.onboarding_welcome_point_offline, R.string.onboarding_welcome_point_optional).forEach {
        Text("•  " + stringResource(it), style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun RoleCard(title: Int, body: Int, available: Boolean, held: Boolean, onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            when {
                held -> Text(
                    stringResource(R.string.onboarding_role_active),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
                !available -> Text(stringResource(R.string.onboarding_role_unavailable), style = MaterialTheme.typography.labelLarge)
                else -> OutlinedButton(onClick = onRequest) { Text(stringResource(R.string.onboarding_role_request)) }
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(group.reason), style = MaterialTheme.typography.bodyMedium)
                when {
                    missing.isEmpty() -> Text(
                        stringResource(R.string.onboarding_perm_granted),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    group in asked -> {
                        Text(stringResource(R.string.onboarding_perm_blocked), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { context.startActivity(appDetailsIntent(context.packageName)) }) {
                            Text(stringResource(R.string.onboarding_perm_open_settings))
                        }
                    }
                    else -> OutlinedButton(onClick = {
                        asked = asked + group
                        launcher.launch(missing.toTypedArray())
                    }) { Text(stringResource(R.string.onboarding_perm_allow)) }
                }
            }
        }
    }
}

private fun appDetailsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
