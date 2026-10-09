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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.contacts.ContactGroup
import com.qtekfun.ultimatephone.core.designsystem.ChoiceChip
import com.qtekfun.ultimatephone.core.designsystem.EmptyState
import com.qtekfun.ultimatephone.core.designsystem.GroupedItem
import com.qtekfun.ultimatephone.core.designsystem.PermissionGate
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.SearchPill
import com.qtekfun.ultimatephone.core.designsystem.SectionHeader
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.UiAction
import kotlinx.coroutines.launch

/** Wide enough to be a comfortable touch target (48dp) along the edge of the list. */
private val IndexWidth = 48.dp

/** Space the letter index takes at the end of the list, minus the margin the cards already have. */
private val IndexReserve = IndexWidth - Spacing.Small

/** Keeps the last rows clear of the "new contact" button. */
private val FabClearance = 88.dp

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
        buttonLabel = stringResource(R.string.contacts_permission_button),
        icon = Icons.Filled.Contacts
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
    val resources = LocalContext.current.resources
    val exportFailed = stringResource(R.string.contactsadv_export_failed)
    LaunchedEffect(viewModel) {
        viewModel.exportResults.collect { result ->
            snackbar.showSnackbar(
                when (result) {
                    is ExportResult.Done -> resources.getQuantityString(R.plurals.contactsadv_export_done, result.count, result.count)
                    ExportResult.Failed -> exportFailed
                }
            )
        }
    }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    ScreenScaffold(
        title = if (selecting) {
            pluralStringResource(R.plurals.contactsadv_selection_count, selection.size, selection.size)
        } else {
            stringResource(R.string.contacts_title)
        },
        actions = {
            if (selecting) {
                IconButton(onClick = { exportLauncher.launch(viewModel.prepareExport(all = false)) }) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.contactsadv_export_selected))
                }
                IconButton(onClick = viewModel::clearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.contactsadv_selection_clear))
                }
            } else {
                MoreMenu(
                    onOpenGroups = onOpenGroups,
                    onOpenDuplicates = onOpenDuplicates,
                    onOpenImport = onOpenImport,
                    onExportAll = { exportLauncher.launch(viewModel.prepareExport(all = true)) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(
                    onClick = onAddContact,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.contacts_add))
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchPill(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.contacts_search_hint),
                clearLabel = stringResource(R.string.contacts_search_clear)
            )
            if (groups.isNotEmpty()) GroupChips(groups, activeGroup, viewModel::chooseGroup)
            when (val current = state) {
                ContactsListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                is ContactsListState.Ready -> when {
                    current.content.isEmpty -> {
                        val noContacts = current.totalContacts == 0 && activeGroup == null
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = if (noContacts) Icons.Filled.Contacts else Icons.Filled.SearchOff,
                                title = stringResource(if (noContacts) R.string.design2_contacts_empty_title else R.string.design2_contacts_noresult_title),
                                body = stringResource(if (noContacts) R.string.contacts_empty else R.string.contacts_no_results),
                                action = if (noContacts) UiAction(stringResource(R.string.contacts_add), onAddContact) else null
                            )
                        }
                    }
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
private fun MoreMenu(onOpenGroups: () -> Unit, onOpenDuplicates: () -> Unit, onOpenImport: () -> Unit, onExportAll: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.contactsadv_menu_more)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
            MenuItem(R.string.contactsadv_menu_groups, onClick = { menu = false }, action = onOpenGroups)
            MenuItem(R.string.contactsadv_menu_duplicates, onClick = { menu = false }, action = onOpenDuplicates)
            MenuItem(R.string.contactsadv_menu_import, onClick = { menu = false }, action = onOpenImport)
            MenuItem(R.string.contactsadv_menu_export_all, onClick = { menu = false }, action = onExportAll)
        }
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            onClick()
            action()
        }
    )
}

@Composable
private fun GroupChips(groups: List<ContactGroup>, active: Long?, onChoose: (Long?) -> Unit) {
    val label = stringResource(R.string.contactsadv_filter_label)
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
    ) {
        item(key = "all") {
            ChoiceChip(label = stringResource(R.string.contactsadv_filter_all), selected = active == null, onClick = { onChoose(null) })
        }
        items(groups, key = { it.id }) { group ->
            ChoiceChip(label = group.title, selected = active == group.id, onClick = { onChoose(if (active == group.id) null else group.id) })
        }
    }
}

/** Position of every contact row inside its run of contacts (a favourites block or one letter), to round only the run's ends. */
private fun runPositions(rows: List<ListRow>): List<Pair<Int, Int>> {
    val result = MutableList(rows.size) { 0 to 1 }
    var start = 0
    while (start < rows.size) {
        if (rows[start] !is ListRow.Item) {
            start++
            continue
        }
        var end = start
        while (end < rows.size && rows[end] is ListRow.Item) end++
        for (i in start until end) result[i] = (i - start) to (end - start)
        start = end
    }
    return result
}

@Composable
private fun ContactRows(content: ContactListContent, showIndex: Boolean, selection: Set<String>, onOpenContact: (String) -> Unit, onSelect: (String) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val withIndex = showIndex && content.indexLetters.size > 1
    val positions = remember(content.rows) { runPositions(content.rows) }
    val reserve = if (withIndex) IndexReserve else 0.dp
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = FabClearance)) {
            items(content.rows.size, key = { content.rows[it].key }) { i ->
                when (val row = content.rows[i]) {
                    ListRow.FavoritesHeader -> SectionHeader(stringResource(R.string.contacts_favorites), Modifier.padding(end = reserve))
                    is ListRow.LetterHeader -> SectionHeader(row.letter, Modifier.padding(end = reserve))
                    is ListRow.Item -> ContactRow(
                        row = row,
                        position = positions[i],
                        selected = row.contact.lookupKey in selection,
                        endReserve = reserve,
                        onOpenContact = onOpenContact,
                        onSelect = onSelect
                    )
                }
            }
        }
        if (withIndex) {
            LetterIndex(
                letters = content.indexLetters,
                onSelect = { letter -> content.positions[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } } },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    row: ListRow.Item,
    position: Pair<Int, Int>,
    selected: Boolean,
    endReserve: Dp,
    onOpenContact: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    val contact = row.contact
    val name = contact.displayName.ifBlank { stringResource(R.string.contact_unnamed) }
    val selectLabel = stringResource(R.string.contactsadv_select_action)
    val favoriteState = stringResource(R.string.contactsadv_field_favorite)
    val color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    GroupedItem(index = position.first, count = position.second, color = color, modifier = Modifier.padding(end = endReserve)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.RowHeight)
                .semantics {
                    this.selected = selected
                    if (contact.starred) stateDescription = favoriteState
                }
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = selectLabel,
                    onLongClick = { onSelect(contact.lookupKey) },
                    onClick = { onOpenContact(contact.lookupKey) }
                )
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            ContactAvatar(name = name, photoUri = contact.photoThumbUri, size = Spacing.MinTarget)
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            when {
                selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                contact.starred && !row.favorite -> Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/** Fast scroller: tap or drag over the letters to jump to that section. */
@Composable
private fun LetterIndex(letters: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    // A touch shortcut only: TalkBack users scroll the list itself, so the strip is hidden from the accessibility tree.
    // Its letters ignore the font scale, otherwise 26 of them could never fit the screen height at large text sizes.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .width(IndexWidth)
                .clearAndSetSemantics {}
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
}

private fun pickLetter(y: Float, height: Int, letters: List<String>, onSelect: (String) -> Unit) {
    onSelect(letters[(y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)])
}
