package com.qtekfun.ultimatephone.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.qtekfun.ultimatephone.R

/** The four top-level destinations. Each owns a navigation graph whose route is [route]. */
enum class TopLevelDestination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    DIALER("dialer", R.string.nav_dialer, Icons.Filled.Dialpad),
    RECENTS("recents", R.string.nav_recents, Icons.Filled.History),
    CONTACTS("contacts", R.string.nav_contacts, Icons.Filled.People),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings)
}

/** Route of the dialer with an optional number already typed in. */
const val DIALER_ROUTE_PATTERN = "dialer?number={number}"
const val DIALER_NUMBER_ARG = "number"

fun dialerRoute(number: String? = null): String = if (number.isNullOrBlank()) "dialer" else "dialer?number=${android.net.Uri.encode(number)}"

/** Opens the dialer with [number] typed in, for example from "Call" buttons that want to confirm the SIM. */
fun NavController.openDialer(number: String?) {
    navigate(dialerRoute(number)) {
        launchSingleTop = true
    }
}

/** Contact screens (owned by the contacts feature). Arguments are URL-encoded by the helpers below. */
const val CONTACT_DETAIL_ROUTE = "contacts/detail/{lookupKey}"
const val CONTACT_EDIT_ROUTE = "contacts/edit?lookupKey={lookupKey}&number={number}"

fun contactDetailRoute(lookupKey: String): String = "contacts/detail/${android.net.Uri.encode(lookupKey)}"

/** Edit [lookupKey], or create a new contact (optionally with [number] already filled in) when it is null. */
fun contactEditRoute(lookupKey: String? = null, number: String? = null): String =
    "contacts/edit?lookupKey=${android.net.Uri.encode(lookupKey.orEmpty())}&number=${android.net.Uri.encode(number.orEmpty())}"

/** Call history detail for one number (owned by the recents feature). */
const val CALL_DETAIL_ROUTE = "recents/detail/{number}"

fun callDetailRoute(number: String): String = "recents/detail/${android.net.Uri.encode(number)}"
