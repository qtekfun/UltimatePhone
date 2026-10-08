package com.qtekfun.ultimatephone.feature.onboarding

import android.Manifest
import androidx.annotation.StringRes
import com.qtekfun.ultimatephone.R

/** A permission shown on its own card, with its own explanation. Related permissions are asked together. */
enum class PermissionGroup(val permissions: List<String>, @StringRes val title: Int, @StringRes val reason: Int) {
    PHONE(
        listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.ANSWER_PHONE_CALLS
        ),
        R.string.onboarding_perm_phone_title,
        R.string.onboarding_perm_phone_reason
    ),
    CONTACTS(
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        R.string.onboarding_perm_contacts_title,
        R.string.onboarding_perm_contacts_reason
    ),
    CALL_LOG(
        listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
        R.string.onboarding_perm_calllog_title,
        R.string.onboarding_perm_calllog_reason
    ),
    NOTIFICATIONS(
        listOf(Manifest.permission.POST_NOTIFICATIONS),
        R.string.onboarding_perm_notifications_title,
        R.string.onboarding_perm_notifications_reason
    );

    /** The permissions of this group that [isGranted] says are still missing. */
    fun missing(isGranted: (String) -> Boolean): List<String> = permissions.filterNot(isGranted)

    fun isGranted(isGranted: (String) -> Boolean): Boolean = missing(isGranted).isEmpty()
}
