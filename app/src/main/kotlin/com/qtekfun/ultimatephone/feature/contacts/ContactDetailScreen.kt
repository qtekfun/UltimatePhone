package com.qtekfun.ultimatephone.feature.contacts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.qtekfun.ultimatephone.core.contacts.ContactDetail
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate

@Composable
fun ContactDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, onDialFallback: (String) -> Unit) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        rationale = stringResource(R.string.contacts_permission_rationale),
        buttonLabel = stringResource(R.string.contacts_permission_button)
    ) {
        ContactDetailContentScreen(onBack, onEdit, onDialFallback)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactDetailContentScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDialFallback: (String) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val contact = (state as? ContactDetailState.Ready)?.contact

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.contact_back)) }
                },
                actions = {
                    if (contact != null) {
                        val starLabel = stringResource(if (contact.starred) R.string.contact_unstar else R.string.contact_star)
                        IconButton(onClick = { viewModel.toggleStar(contact.starred) }) {
                            Icon(if (contact.starred) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = starLabel)
                        }
                        IconButton(onClick = {
                            onEdit(contact.lookupKey)
                        }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.contact_edit)) }
                        IconButton(onClick = { share(context, contact.lookupKey) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.contact_share))
                        }
                        IconButton(onClick = {
                            confirmDelete = true
                        }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.contact_delete)) }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                ContactDetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                ContactDetailState.NotFound -> Text(
                    stringResource(R.string.contact_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                is ContactDetailState.Ready -> DetailBody(
                    contact = current.contact,
                    onCall = { number -> if (!viewModel.call(number)) onDialFallback(number) }
                )
            }
        }
    }

    if (confirmDelete && contact != null) {
        val shownName = contact.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.contact_delete_title)) },
            text = { Text(stringResource(R.string.contact_delete_message, shownName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text(stringResource(R.string.contact_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.contact_cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailBody(contact: ContactDetail, onCall: (String) -> Unit) {
    val context = LocalContext.current
    val name = contact.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContactAvatar(name = name, photoUri = contact.photoUri, size = 112.dp)
            Text(name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (contact.organization.isNotBlank()) {
                Text(contact.organization, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (contact.phones.isNotEmpty()) {
            Section(R.string.contact_section_phones)
            contact.phones.forEach { stored ->
                val number = stored.value.value
                ListItem(
                    headlineContent = { Text(number) },
                    supportingContent = { Text(stored.typeLabel) },
                    trailingContent = {
                        FilledTonalIconButton(onClick = { onCall(number) }) {
                            Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.contact_call, number))
                        }
                    },
                    modifier = Modifier.clickable { onCall(number) }
                )
            }
        }
        if (contact.emails.isNotEmpty()) {
            Section(R.string.contact_section_emails)
            contact.emails.forEach { stored ->
                val address = stored.value.value
                ListItem(
                    headlineContent = { Text(address) },
                    supportingContent = { Text(stored.typeLabel) },
                    trailingContent = { Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.contact_email_to, address)) },
                    modifier = Modifier.clickable { sendEmail(context, address) }
                )
            }
        }
        contact.birthday?.let { birthday ->
            Section(R.string.contact_section_birthday)
            ListItem(headlineContent = { Text(birthdayText(context, birthday)) })
        }
        if (contact.notes.isNotBlank()) {
            Section(R.string.contact_section_notes)
            ListItem(headlineContent = { Text(contact.notes) })
        }
        contact.account?.let { account ->
            Section(R.string.contact_section_account)
            ListItem(headlineContent = { Text(accountText(context, account)) })
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

private fun sendEmail(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.contact_share_failed, Toast.LENGTH_SHORT).show()
    }
}

/** Shares the contact as a vCard straight from the contacts provider, so every field and the photo go along. */
private fun share(context: Context, lookupKey: String) {
    val vcard = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, Uri.encode(lookupKey))
    val send = Intent(Intent.ACTION_SEND).apply {
        type = ContactsContract.Contacts.CONTENT_VCARD_TYPE
        putExtra(Intent.EXTRA_STREAM, vcard)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.contact_share_failed, Toast.LENGTH_SHORT).show()
    }
}
