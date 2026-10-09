package com.qtekfun.ultimatephone.feature.contacts

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.contacts.ContactAccount
import com.qtekfun.ultimatephone.core.contacts.ContactDate
import com.qtekfun.ultimatephone.core.contacts.DraftError
import com.qtekfun.ultimatephone.core.contacts.LabeledValue
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.EmptyState
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SettingsGroup
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ContactEditScreen(onClose: () -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        rationale = stringResource(R.string.contacts_permission_rationale),
        buttonLabel = stringResource(R.string.contacts_permission_button)
    ) {
        ContactEditContentScreen(onClose)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactEditContentScreen(onClose: () -> Unit, viewModel: ContactEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onClose() } }

    ScreenScaffold(
        title = stringResource(if (state.isNew) R.string.edit_title_new else R.string.edit_title_edit),
        onBack = onClose,
        actions = {
            TextButton(onClick = viewModel::save, enabled = !state.loading && !state.saving && !state.missing) {
                Text(stringResource(R.string.edit_save), style = MaterialTheme.typography.labelLarge)
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                state.missing -> EmptyState(
                    icon = Icons.Filled.PersonOff,
                    title = stringResource(R.string.contact_not_found),
                    body = stringResource(R.string.design2_contact_missing_body),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> EditForm(state, viewModel)
            }
        }
    }
}

/** A rounded group of form fields, with an optional title above it. */
@Composable
private fun FormGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    SettingsGroup(title = title, modifier = Modifier.padding(horizontal = Spacing.Medium)) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            content = content
        )
    }
}

@Composable
private fun EditForm(state: ContactEditState, viewModel: ContactEditViewModel) {
    val draft = state.draft
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        FormGroup(title = null) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.edit_name)) },
                singleLine = true,
                isError = state.validationError == DraftError.NAME_OR_PHONE_REQUIRED,
                supportingText = if (state.validationError == DraftError.NAME_OR_PHONE_REQUIRED) {
                    { Text(stringResource(R.string.edit_error_required)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.organization,
                onValueChange = viewModel::setOrganization,
                label = { Text(stringResource(R.string.edit_organization)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        FormGroup(title = stringResource(R.string.contactsadv_field_phones)) {
            draft.phones.forEachIndexed { index, phone ->
                LabeledField(
                    value = phone,
                    label = R.string.edit_phone,
                    typeDescription = R.string.edit_type_phone,
                    removeDescription = R.string.edit_remove_phone,
                    keyboardType = KeyboardType.Phone,
                    choices = PHONE_LABEL_CHOICES,
                    onChange = { viewModel.setPhone(index, it) },
                    onRemove = { viewModel.removePhone(index) }
                )
            }
            AddButton(R.string.edit_add_phone, viewModel::addPhone)
        }

        FormGroup(title = stringResource(R.string.contactsadv_field_emails)) {
            draft.emails.forEachIndexed { index, email ->
                LabeledField(
                    value = email,
                    label = R.string.edit_email,
                    typeDescription = R.string.edit_type_email,
                    removeDescription = R.string.edit_remove_email,
                    keyboardType = KeyboardType.Email,
                    choices = EMAIL_LABEL_CHOICES,
                    onChange = { viewModel.setEmail(index, it) },
                    onRemove = { viewModel.removeEmail(index) }
                )
            }
            AddButton(R.string.edit_add_email, viewModel::addEmail)
        }

        FormGroup(title = stringResource(R.string.contactsadv_field_birthday)) {
            BirthdayField(draft.birthday, viewModel::setBirthday)
        }

        FormGroup(title = stringResource(R.string.contactsadv_field_notes)) {
            OutlinedTextField(
                value = draft.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.edit_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.isNew && state.accounts.size > 1) {
            FormGroup(title = null) { AccountPicker(draft.account, state.accounts, viewModel::setAccount) }
        }
        if (state.saveFailed) {
            InfoBanner(
                kind = BannerKind.Error,
                title = stringResource(R.string.edit_error_save),
                modifier = Modifier.padding(horizontal = Spacing.Medium),
                liveRegion = true
            )
        }
    }
}

@Composable
private fun AddButton(label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = Spacing.MinTarget)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(label), modifier = Modifier.padding(start = Spacing.Small))
    }
}

@Composable
private fun LabeledField(
    value: LabeledValue,
    label: Int,
    typeDescription: Int,
    removeDescription: Int,
    keyboardType: KeyboardType,
    choices: List<LabelChoice>,
    onChange: (LabeledValue) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    // With large text the type chip would squeeze the field to a sliver: it drops below the field instead.
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    var expanded by remember { mutableStateOf(false) }
    val shown = labelText(context, choices, value)
    val spoken = stringResource(typeDescription, shown)
    val typeChip: @Composable () -> Unit = {
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(shown) },
                modifier = Modifier.padding(horizontal = Spacing.XSmall).semantics { contentDescription = spoken }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.medium) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(stringResource(choice.label)) },
                        onClick = {
                            expanded = false
                            onChange(value.copy(type = choice.type, customLabel = null))
                        }
                    )
                }
            }
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            OutlinedTextField(
                value = value.value,
                onValueChange = { onChange(value.copy(value = it)) },
                label = { Text(stringResource(label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.weight(1f)
            )
            if (!stacked) typeChip()
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Remove, contentDescription = stringResource(removeDescription)) }
        }
        if (stacked) typeChip()
    }
}

private const val STACK_FONT_SCALE = 1.3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayField(birthday: ContactDate?, onChange: (ContactDate?) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
            Text(if (birthday == null) stringResource(R.string.edit_birthday_add) else birthdayText(context, birthday))
        }
        if (birthday != null) {
            IconButton(onClick = { onChange(null) }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.edit_birthday_remove)) }
        }
    }
    if (picking) {
        val initial = LocalDate.of(birthday?.year ?: DEFAULT_PICKER_YEAR, birthday?.month ?: 1, minOf(birthday?.day ?: 1, DAYS_SAFE_IN_MONTH))
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(ContactDate(date.year, date.monthValue, date.dayOfMonth))
                    }
                }) { Text(stringResource(R.string.edit_birthday_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.contact_cancel)) } },
            shape = MaterialTheme.shapes.extraLarge
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPicker(selected: ContactAccount, accounts: List<ContactAccount>, onSelect: (ContactAccount) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = accountText(context, selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.edit_account)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(accountText(context, account)) },
                    onClick = {
                        expanded = false
                        onSelect(account)
                    }
                )
            }
        }
    }
}

private const val DEFAULT_PICKER_YEAR = 1990
private const val DAYS_SAFE_IN_MONTH = 28
