package com.qtekfun.ultimatephone.core.spam.lists

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * One recorded decision for an incoming call, kept so the app can explain later why a number was flagged and so the
 * history can show a badge. [level], [action] and [reason] hold enum names. [sourceId] is the source id, the rule id
 * or `own`. Only the E.164 number is stored, never the raw string the system reported.
 */
@Entity(tableName = "call_decision", indices = [Index(value = ["e164"]), Index(value = ["decidedAt"])])
data class CallDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val e164: String,
    val decidedAt: Long,
    val level: String?,
    val action: String,
    val sourceId: String?,
    val label: String?,
    val reason: String,
    val informational: Boolean
)

@Dao
interface CallDecisionDao {
    @Insert
    suspend fun insert(row: CallDecisionEntity)

    /** The newest row of every number. */
    @Query("SELECT * FROM call_decision WHERE id IN (SELECT MAX(id) FROM call_decision GROUP BY e164)")
    fun observeLatest(): Flow<List<CallDecisionEntity>>

    @Query("SELECT * FROM call_decision WHERE e164 = :e164 ORDER BY id DESC LIMIT 1")
    suspend fun latestFor(e164: String): CallDecisionEntity?

    @Query("DELETE FROM call_decision WHERE decidedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
