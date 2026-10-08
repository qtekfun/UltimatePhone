package com.qtekfun.ultimatephone.feature.contacts

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.contacts.ContactGroup
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate
import kotlinx.coroutines.launch

@Composable
fun ContactsListScreen(
    onOpenContact: (String) -> Unit,
    onAddContact: () -> Unit,
    onOpenGroups: () -> Unit = {},
    onOpenDuplicates: () -> Unit = {},
    onOpenImport: () -> Unit = {}
) {
    PermissionGate(
        permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        rationale = stringResource(R.string.contacts_permission_rationale),
        buttonLabel = stringResource(R.string.contacts_permission_button)
    ) {
        ContactsListContentScreen(onOpenContact, onAddContact, onOpenGroups, onOpenDuplicates, onOpenImport)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactsListContentScreen(
    onOpenContact: (String) -> Unit,
    onAddContact: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenImport: () -> Unit,
    viewModel: ContactsListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val selecting = selection.isNotEmpty()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(VCARD_MIME)) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val exportedMany = stringResource(R.string.contactsadv_export_done)
    val exportFailed = stringResource(R.string.contactsadv_export_failed)
    LaunchedEffect(viewModel) {
        viewModel.exportResults.collect { result ->
            snackbar.showSnackbar(
                when (result) {
                    is ExportResult.Done -> exportedMany.format(result.count)
                    ExportResult.Failed -> exportFailed
                }
            )
        }
    }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(onClick = onAddContact) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.contacts_add))
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (selecting) {
                SelectionBar(
                    count = selection.size,
                    onExport = { exportLauncher.launch(viewModel.prepareExport(all = false)) },
                    onClear = viewModel::clearSelection
                )
            } else {
                HeaderBar(
                    onOpenGroups = onOpenGroups,
                    onOpenDuplicates = onOpenDuplicates,
                    onOpenImport = onOpenImport,
                    onExportAll = { exportLauncher.launch(viewModel.prepareExport(all = true)) }
                )
            }
            SearchField(query, viewModel::onQueryChange)
            if (groups.isNotEmpty()) GroupChips(groups, activeGroup, viewModel::chooseGroup)
            when (val current = state) {
                ContactsListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                is ContactsListState.Ready -> when {
                    current.content.isEmpty -> EmptyMessage(
                        if (current.totalContacts == 0 &&
                            activeGroup == null
                        ) {
                            R.string.contacts_empty
                        } else {
                            R.string.contacts_no_results
                        }
                    )
                    else -> ContactRows(
                        content = current.content,
                        showIndex = query.isBlank(),
                        selection = selection,
                        onOpenContact = { key -> if (selecting) viewModel.toggleSelection(key) else onOpenContact(key) },
                        onSelect = viewModel::toggleSelection
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(onOpenGroups: () -> Unit, onOpenDuplicates: () -> Unit, onOpenImport: () -> Unit, onExportAll: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.contacts_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.contactsadv_menu_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.contactsadv_menu_groups)) },
                    onClick = {
                        menu = false
                        onOpenGroups()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.contactsadv_menu_duplicates)) },
                    onClick = {
                        menu = false
                        onOpenDuplicates()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.contactsadv_menu_import)) },
                    onClick = {
                        menu = false
                        onOpenImport()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.contactsadv_menu_export_all)) },
                    onClick = {
                        menu = false
                        onExportAll()
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onExport: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.contactsadv_selection_clear)) }
        Text(
            stringResource(R.string.contactsadv_selection_count, count),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        IconButton(onClick = onExport) { Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.contactsadv_export_selected)) }
    }
}

@Composable
private fun GroupChips(groups: List<ContactGroup>, active: Long?, onChoose: (Long?) -> Unit) {
    val label = stringResource(R.string.contactsadv_filter_label)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
    ) {
        item(key = "all") {
            FilterChip(selected = active == null, onClick = { onChoose(null) }, label = { Text(stringResource(R.string.contactsadv_filter_all)) })
        }
        items(groups, key = { it.id }) { group ->
            FilterChip(selected = active == group.id, onClick = { onChoose(if (active == group.id) null else group.id) }, label = { Text(group.title) })
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.contacts_search_clear)) }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyMessage(message: Int) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ContactRows(content: ContactListContent, showIndex: Boolean, selection: Set<String>, onOpenContact: (String) -> Unit, onSelect: (String) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        // The bottom padding keeps the last rows clear of the "new contact" button.
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(content.rows, key = { it.key }) { row ->
                when (row) {
                    ListRow.FavoritesHeader -> SectionHeader(stringResource(R.string.contacts_favorites))
                    is ListRow.LetterHeader -> SectionHeader(row.letter)
                    is ListRow.Item -> ContactRow(row, row.contact.lookupKey in selection, onOpenContact, onSelect)
                }
            }
        }
        if (showIndex && content.indexLetters.size > 1) {
            LetterIndex(
                letters = content.indexLetters,
                onSelect = { letter -> content.positions[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } } },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 40.dp, top = 12.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(row: ListRow.Item, selected: Boolean, onOpenContact: (String) -> Unit, onSelect: (String) -> Unit) {
    val contact = row.contact
    val name = contact.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
    val selectLabel = stringResource(R.string.contactsadv_select_action)
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { ContactAvatar(name = name, photoUri = contact.photoThumbUri) },
        trailingContent = when {
            selected -> {
                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            }
            contact.starred && !row.favorite -> {
                { Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) }
            }
            else -> null
        },
        colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 24.dp)
            .semantics { this.selected = selected }
            .combinedClickable(onLongClickLabel = selectLabel, onLongClick = { onSelect(contact.lookupKey) }, onClick = { onOpenContact(contact.lookupKey) })
    )
}

/** Fast scroller: tap or drag over the letters to jump to that section. */
@Composable
private fun LetterIndex(letters: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.contacts_index_description)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(letters) {
                detectTapGestures(onTap = { offset -> pickLetter(offset.y, size.height, letters, onSelect) })
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> pickLetter(offset.y, size.height, letters, onSelect) },
                    onVerticalDrag = { change, _ -> pickLetter(change.position.y, size.height, letters, onSelect) }
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

private fun pickLetter(y: Float, height: Int, letters: List<String>, onSelect: (String) -> Unit) {
    onSelect(letters[(y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)])
}
