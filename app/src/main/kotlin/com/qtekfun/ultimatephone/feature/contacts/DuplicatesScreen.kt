package com.qtekfun.ultimatephone.feature.contacts

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.contacts.DuplicateCluster
import com.qtekfun.ultimatephone.core.contacts.DuplicateConfidence
import com.qtekfun.ultimatephone.core.contacts.DuplicateReason
import com.qtekfun.ultimatephone.core.contacts.LabeledValue
import com.qtekfun.ultimatephone.core.contacts.MergeSource
import com.qtekfun.ultimatephone.core.contacts.MergedContact
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.ConfirmDialog
import com.qtekfun.ultimatephone.core.designsystem.EmptyState
import com.qtekfun.ultimatephone.core.designsystem.GroupedItem
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SectionHeader
import com.qtekfun.ultimatephone.core.designsystem.SettingsRow
import com.qtekfun.ultimatephone.core.designsystem.Spacing

@Composable
fun DuplicatesScreen(onBack: () -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        rationale = stringResource(R.string.contacts_permission_rationale),
        buttonLabel = stringResource(R.string.contacts_permission_button)
    ) {
        DuplicatesContentScreen(onBack)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DuplicatesContentScreen(onBack: () -> Unit, viewModel: DuplicatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val review = state.review
    var confirming by remember { mutableStateOf(false) }

    val messageText = when (state.message) {
        DuplicatesMessage.MERGED -> stringResource(R.string.contactsadv_msg_merged)
        DuplicatesMessage.MERGE_FAILED -> stringResource(R.string.contactsadv_msg_merge_failed)
        DuplicatesMessage.IGNORED -> stringResource(R.string.contactsadv_msg_ignored)
        DuplicatesMessage.LOAD_FAILED -> stringResource(R.string.contactsadv_msg_load_failed)
        null -> null
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }
    BackHandler(enabled = review != null) { viewModel.closeReview() }

    ScreenScaffold(
        title = stringResource(if (review == null) R.string.contactsadv_dup_title else R.string.contactsadv_review_title),
        onBack = { if (review != null) viewModel.closeReview() else onBack() },
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (review == null && state.clusters.size > 1) {
                TextButton(onClick = viewModel::ignoreAll, modifier = Modifier.heightIn(min = Spacing.MinTarget)) {
                    Text(stringResource(R.string.contactsadv_dup_ignore_all))
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading || state.reviewLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                review != null -> ReviewBody(
                    review = review,
                    busy = state.busy,
                    onToggle = viewModel::toggle,
                    onMerge = { confirming = true },
                    onNotDuplicates = { viewModel.notDuplicates(review.cluster) }
                )
                state.clusters.isEmpty() -> EmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = stringResource(R.string.contactsadv_dup_empty),
                    body = stringResource(R.string.design2_dup_empty_body),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> ClusterList(state.clusters, viewModel::open)
            }
        }
    }

    val merged = review?.merged
    if (confirming && review != null && merged != null) {
        val name = merged.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
        ConfirmDialog(
            title = stringResource(R.string.contactsadv_review_confirm_title),
            body = stringResource(R.string.contactsadv_review_confirm_message, review.included.size, name),
            confirmLabel = stringResource(R.string.contactsadv_review_merge),
            dismissLabel = stringResource(R.string.contact_cancel),
            onConfirm = viewModel::confirmMerge,
            onDismiss = { confirming = false }
        )
    }
}

@Composable
private fun ClusterList(clusters: List<DuplicateCluster>, onOpen: (DuplicateCluster) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.Medium)) {
        item { SectionHeader(stringResource(R.string.contactsadv_dup_count, clusters.size)) }
        items(clusters.size, key = { clusters[it].id }) { index ->
            GroupedItem(index = index, count = clusters.size) { ClusterRow(clusters[index], onOpen) }
        }
    }
}

@Composable
private fun ClusterRow(cluster: DuplicateCluster, onOpen: (DuplicateCluster) -> Unit) {
    val unnamed = stringResource(R.string.contact_unnamed)
    val names = cluster.members.joinToString(", ") { it.displayName.ifBlank { unnamed } }
    val reasonLabels = cluster.reasons.map { stringResource(reasonText(it)) }
    val reasons = reasonLabels.joinToString(" · ")
    val confidence = stringResource(
        if (cluster.confidence == DuplicateConfidence.HIGH) R.string.contactsadv_dup_confidence_high else R.string.contactsadv_dup_confidence_medium
    )
    SettingsRow(
        title = names,
        summary = "$reasons · $confidence · " + pluralStringResource(R.plurals.contactsadv_dup_members, cluster.members.size, cluster.members.size),
        icon = Icons.Filled.People,
        onClick = { onOpen(cluster) }
    )
}

private fun reasonText(reason: DuplicateReason): Int = when (reason) {
    DuplicateReason.SAME_NUMBER -> R.string.contactsadv_dup_reason_number
    DuplicateReason.SAME_EMAIL -> R.string.contactsadv_dup_reason_email
    DuplicateReason.SIMILAR_NAME -> R.string.contactsadv_dup_reason_name
}

@Composable
private fun ReviewBody(review: ReviewState, busy: Boolean, onToggle: (String) -> Unit, onMerge: () -> Unit, onNotDuplicates: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        InfoBanner(
            kind = BannerKind.Info,
            title = stringResource(R.string.contactsadv_review_nothing_deleted),
            modifier = Modifier.padding(horizontal = Spacing.Medium)
        )
        // Side by side: one card per contact, scrolling sideways when they do not fit.
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            review.sources.forEach { source ->
                SourceCard(source, included = source.lookupKey in review.included, onToggle = { onToggle(source.lookupKey) }, context = context)
            }
        }
        val merged = review.merged
        if (merged != null) {
            Column {
                SectionHeader(stringResource(R.string.contactsadv_review_result))
                MergedCard(merged, review.sources, context)
            }
        }
        if (review.included.size < 2) {
            InfoBanner(
                kind = BannerKind.Warning,
                title = stringResource(R.string.contactsadv_review_need_two),
                modifier = Modifier.padding(horizontal = Spacing.Medium),
                liveRegion = true
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium), horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            OutlinedButton(onClick = onNotDuplicates, enabled = !busy, modifier = Modifier.weight(1f).defaultMinSize(minHeight = Spacing.MinTarget)) {
                Text(stringResource(R.string.contactsadv_review_not_dups))
            }
            Button(onClick = onMerge, enabled = review.canMerge && !busy, modifier = Modifier.weight(1f).defaultMinSize(minHeight = Spacing.MinTarget)) {
                Text(stringResource(R.string.contactsadv_review_merge))
            }
        }
    }
}

private val SourceCardWidth = 260.dp
private val IncludedBorder = 2.dp

@Composable
private fun SourceCard(source: MergeSource, included: Boolean, onToggle: () -> Unit, context: android.content.Context) {
    val name = source.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (included) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(IncludedBorder, if (included) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(SourceCardWidth)
    ) {
        Column(Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val description = stringResource(R.string.contactsadv_review_include, name)
                Checkbox(checked = included, onCheckedChange = { onToggle() }, modifier = Modifier.semantics { contentDescription = description })
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            FieldBlock(R.string.contactsadv_field_organization, listOf(source.organization))
            FieldBlock(R.string.contactsadv_field_phones, source.phones.map { it.value })
            FieldBlock(R.string.contactsadv_field_emails, source.emails.map { it.value })
            FieldBlock(R.string.contactsadv_field_birthday, listOfNotNull(source.birthday?.let { birthdayText(context, it) }))
            FieldBlock(R.string.contactsadv_field_notes, source.notes)
            FieldBlock(R.string.contactsadv_field_account, listOfNotNull(source.account?.let { accountText(context, it) }))
            val photoText = stringResource(if (source.photoUri != null) R.string.contactsadv_photo_yes else R.string.contactsadv_photo_no)
            FieldBlock(R.string.contactsadv_field_photo, listOf(photoText))
            if (source.starred) FieldBlock(R.string.contactsadv_field_favorite, listOf(stringResource(R.string.contactsadv_yes)))
        }
    }
}

@Composable
private fun MergedCard(merged: MergedContact, sources: List<MergeSource>, context: android.content.Context) {
    val name = merged.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium)
    ) {
        Column(Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            FieldBlock(R.string.contactsadv_field_other_names, merged.alternativeNames)
            FieldBlock(R.string.contactsadv_field_organization, listOf(merged.organization))
            FieldBlock(R.string.contactsadv_field_other_organizations, merged.otherOrganizations)
            FieldBlock(R.string.contactsadv_field_phones, merged.phones.map(::valueLine))
            FieldBlock(R.string.contactsadv_field_emails, merged.emails.map(::valueLine))
            FieldBlock(R.string.contactsadv_field_birthday, listOfNotNull(merged.birthday?.let { birthdayText(context, it) }))
            FieldBlock(R.string.contactsadv_field_other_birthdays, merged.otherBirthdays.map { birthdayText(context, it) })
            FieldBlock(R.string.contactsadv_field_notes, listOf(merged.notes))
            val photoFrom = sources.firstOrNull { it.lookupKey == merged.photoSourceLookupKey }?.displayName
            FieldBlock(
                R.string.contactsadv_field_photo,
                listOf(if (photoFrom != null) stringResource(R.string.contactsadv_photo_from, photoFrom) else stringResource(R.string.contactsadv_photo_no))
            )
            if (merged.starred) FieldBlock(R.string.contactsadv_field_favorite, listOf(stringResource(R.string.contactsadv_yes)))
        }
    }
}

private fun valueLine(value: LabeledValue) = value.value

@Composable
private fun FieldBlock(title: Int, lines: List<String>) {
    val shown = lines.filter { it.isNotBlank() }
    if (shown.isEmpty()) return
    Column(Modifier.padding(top = Spacing.XSmall)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        shown.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
