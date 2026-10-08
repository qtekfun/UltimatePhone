package com.qtekfun.ultimatephone.core.calllog

import kotlinx.coroutines.flow.Flow

/**
 * The system call log. Everything degrades quietly: without READ_CALL_LOG the history is empty, without
 * WRITE_CALL_LOG the write operations do nothing and return 0.
 */
interface CallLogRepository {
    /** Calls matching [filter], newest first; re-emits whenever the call log changes. */
    fun observe(filter: CallLogFilter = CallLogFilter.All): Flow<List<CallLogEntry>>

    /** Deletes the entries with these ids and returns how many rows went away. */
    suspend fun delete(ids: Collection<Long>): Int

    /** Deletes the whole history and returns how many rows went away. */
    suspend fun clearAll(): Int

    /** Marks every unseen entry as seen (clears the missed-call badge). */
    suspend fun markAllRead()
}
