package com.qtekfun.ultimatephone.feature.recents

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.ui.graphics.vector.ImageVector
import com.qtekfun.ultimatephone.R

/** One button on the number detail screen. */
data class DetailAction(val id: DetailActionId, @StringRes val label: Int, val icon: ImageVector, val onClick: () -> Unit)

enum class DetailActionId { CALL, CREATE_CONTACT, ADD_TO_CONTACT, OPEN_CONTACT, EDIT_CONTACT, DELETE }

/** What each action does; the screen wires these to navigation and the view model. */
data class DetailHandlers(
    val onCall: () -> Unit,
    val onCreateContact: () -> Unit,
    val onAddToExistingContact: () -> Unit,
    val onOpenContact: (lookupKey: String) -> Unit,
    val onEditContact: (lookupKey: String) -> Unit,
    val onDelete: () -> Unit
)

/**
 * The actions available for a number, in display order. Only actions that work are listed.
 *
 * Extension point: further actions (the Phase 2 spam and whitelist actions) are appended to this list when their
 * feature exists; the screen renders whatever the list contains.
 */
fun detailActions(caller: CallerInfo?, isPrivate: Boolean, hasCalls: Boolean, handlers: DetailHandlers): List<DetailAction> = buildList {
    if (!isPrivate) {
        add(DetailAction(DetailActionId.CALL, R.string.recents_action_call, Icons.Filled.Call, handlers.onCall))
        val contact = caller?.contact
        if (contact == null) {
            add(DetailAction(DetailActionId.CREATE_CONTACT, R.string.recents_action_create_contact, Icons.Filled.PersonAdd, handlers.onCreateContact))
            add(DetailAction(DetailActionId.ADD_TO_CONTACT, R.string.recents_action_add_to_contact, Icons.Filled.PersonSearch, handlers.onAddToExistingContact))
        } else {
            add(
                DetailAction(DetailActionId.OPEN_CONTACT, R.string.recents_action_open_contact, Icons.Filled.Person) {
                    handlers.onOpenContact(contact.lookupKey)
                }
            )
            add(
                DetailAction(DetailActionId.EDIT_CONTACT, R.string.recents_action_edit_contact, Icons.Filled.Edit) {
                    handlers.onEditContact(contact.lookupKey)
                }
            )
        }
    }
    if (hasCalls) add(DetailAction(DetailActionId.DELETE, R.string.recents_action_delete, Icons.Filled.Delete, handlers.onDelete))
}

/**
 * The lookup key inside a contact URI picked with the system contact picker
 * (`content://com.android.contacts/contacts/lookup/{key}/{id}`), still URL-encoded; null for any other shape.
 */
fun lookupKeyFromContactUri(uri: String): String? {
    val marker = "/contacts/lookup/"
    val index = uri.indexOf(marker)
    if (index < 0) return null
    return uri.substring(index + marker.length).substringBefore('/').substringBefore('?').ifEmpty { null }
}
