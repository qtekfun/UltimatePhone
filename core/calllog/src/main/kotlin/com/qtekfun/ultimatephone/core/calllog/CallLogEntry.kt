package com.qtekfun.ultimatephone.core.calllog

import com.qtekfun.ultimatephone.core.telecom.SimAccount

/** What happened to a call, mapped from the `CallLog.Calls.TYPE` column. */
enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    VOICEMAIL,
    ANSWERED_EXTERNALLY,
    UNKNOWN;

    /** Coarse direction used to decide which consecutive calls can share a row. */
    val directionClass: DirectionClass
        get() = when (this) {
            INCOMING, ANSWERED_EXTERNALLY -> DirectionClass.INCOMING
            OUTGOING -> DirectionClass.OUTGOING
            MISSED -> DirectionClass.MISSED
            REJECTED -> DirectionClass.REJECTED
            BLOCKED -> DirectionClass.BLOCKED
            VOICEMAIL -> DirectionClass.VOICEMAIL
            UNKNOWN -> DirectionClass.UNKNOWN
        }

    companion object {
        // Same values as android.provider.CallLog.Calls; kept here so the mapping is a pure, testable function.
        private const val CODE_INCOMING = 1
        private const val CODE_OUTGOING = 2
        private const val CODE_MISSED = 3
        private const val CODE_VOICEMAIL = 4
        private const val CODE_REJECTED = 5
        private const val CODE_BLOCKED = 6
        private const val CODE_ANSWERED_EXTERNALLY = 7

        fun fromCode(code: Int): CallType = when (code) {
            CODE_INCOMING -> INCOMING
            CODE_OUTGOING -> OUTGOING
            CODE_MISSED -> MISSED
            CODE_VOICEMAIL -> VOICEMAIL
            CODE_REJECTED -> REJECTED
            CODE_BLOCKED -> BLOCKED
            CODE_ANSWERED_EXTERNALLY -> ANSWERED_EXTERNALLY
            else -> UNKNOWN
        }
    }
}

/** Some dialers store hidden caller ids as a negative number instead of leaving the column empty. */
fun isHiddenNumber(number: String): Boolean {
    val trimmed = number.trim()
    return trimmed.none { it.isDigit() } || (trimmed.length == 2 && trimmed[0] == '-')
}

/** See [CallType.directionClass]. */
enum class DirectionClass { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL, UNKNOWN }

/**
 * One row of the system call log.
 *
 * @property number the raw number as stored; blank for hidden caller ids.
 * @property durationSeconds talk time, 0 for unanswered calls.
 * @property simComponent raw `PHONE_ACCOUNT_COMPONENT_NAME` column (flattened component name), null when unknown.
 * @property simId raw `PHONE_ACCOUNT_ID` column.
 * @property sim the SIM resolved from [simComponent] and [simId], null when the phone no longer knows it.
 * @property cachedName the name the system cached when the call happened.
 * @property isNew true until the user has seen the entry (the `NEW` column).
 */
data class CallLogEntry(
    val id: Long,
    val number: String,
    val type: CallType,
    val dateMillis: Long,
    val durationSeconds: Long,
    val simComponent: String?,
    val simId: String?,
    val sim: SimAccount?,
    val cachedName: String?,
    val isNew: Boolean
) {
    /** True for hidden or unknown caller ids (blank, text, or the "-1"/"-2"/"-3" markers): nothing to call back or open. */
    val isPrivateNumber: Boolean get() = isHiddenNumber(number)
}
