package com.qtekfun.ultimatephone.feature.contacts

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.contacts.ContactAccount
import com.qtekfun.ultimatephone.core.contacts.ContactGroup
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate

@Composable
fun GroupsScreen(onBack: () -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        rationale = stringResource(R.string.contacts_permission_rationale),
        buttonLabel = stringResource(R.string.contacts_permission_button)
    ) {
        GroupsContentScreen(onBack)
    }
}

/** What the group dialog is doing. */
private sealed interface GroupDialog {
    data object Create : GroupDialog

    data class Rename(val group: ContactGroup) : GroupDialog

    data class Delete(val group: ContactGroup) : GroupDialog
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupsContentScreen(onBack: () -> Unit, viewModel: GroupsViewModel = hiltViewModel()) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<GroupDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.contactsadv_groups_failed)
    LaunchedEffect(state.failed) {
        if (state.failed) {
            snackbar.showSnackbar(failedText)
            viewModel.failureShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contactsadv_groups_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.contact_back)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = GroupDialog.Create }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.contactsadv_groups_add))
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val list = groups
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                list.isEmpty() -> Text(
                    stringResource(R.string.contactsadv_groups_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(list, key = { it.id }) { group ->
                        GroupRow(group, onRename = { dialog = GroupDialog.Rename(group) }, onDelete = { dialog = GroupDialog.Delete(group) })
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        null -> Unit
        GroupDialog.Create -> GroupNameDialog(
            title = R.string.contactsadv_groups_create_title,
            confirm = R.string.contactsadv_groups_create,
            initial = "",
            accounts = state.accounts,
            onDismiss = { dialog = null },
            onConfirm = { name, account ->
                dialog = null
                viewModel.create(name, account)
            }
        )
        is GroupDialog.Rename -> GroupNameDialog(
            title = R.string.contactsadv_groups_rename_title,
            confirm = R.string.contactsadv_groups_rename,
            initial = current.group.title,
            accounts = null,
            onDismiss = { dialog = null },
            onConfirm = { name, _ ->
                dialog = null
                viewModel.rename(current.group, name)
            }
        )
        is GroupDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.contactsadv_groups_delete_title)) },
            text = { Text(stringResource(R.string.contactsadv_groups_delete_message, current.group.title)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    viewModel.delete(current.group)
                }) { Text(stringResource(R.string.contact_delete)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.contact_cancel)) } }
        )
    }
}

@Composable
private fun GroupRow(group: ContactGroup, onRename: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val account = if (group.account.isLocal) "" else accountText(context, group.account) + " · "
    val readOnly = if (group.editable) "" else " · " + stringResource(R.string.contactsadv_groups_read_only)
    ListItem(
        headlineContent = { Text(group.title) },
        supportingContent = { Text(account + stringResource(R.string.contactsadv_groups_members, group.memberCount) + readOnly) },
        trailingContent = if (group.editable) {
            {
                Row {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.contactsadv_groups_rename_desc, group.title))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.contactsadv_groups_delete_desc, group.title))
                    }
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupNameDialog(
    title: Int,
    confirm: Int,
    initial: String,
    accounts: List<ContactAccount>?,
    onDismiss: () -> Unit,
    onConfirm: (String, ContactAccount) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial) }
    var account by remember { mutableStateOf(ContactAccount.LOCAL) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.contactsadv_groups_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (accounts != null && accounts.size > 1) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(top = 12.dp)) {
                        OutlinedTextField(
                            value = accountText(context, account),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.contactsadv_groups_account_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            accounts.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(accountText(context, candidate)) },
                                    onClick = {
                                        expanded = false
                                        account = candidate
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, account) }, enabled = name.isNotBlank()) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.contact_cancel)) } }
    )
}
