package com.qtekfun.ultimatephone.core.spam.lists

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.Flow

/**
 * Row of either user list. [list] holds the [ListType] name, [kind] the [EntryKind] name. Both lists share one table
 * because they have the same sync shape; every query filters by [list].
 */
@Entity(
    tableName = "list_entry",
    indices = [Index(value = ["list", "value"]), Index(value = ["list", "kind", "value"])]
)
data class ListEntryEntity(
    @PrimaryKey val id: String,
    val list: String,
    val kind: String,
    val value: String,
    val label: String?,
    val note: String?,
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean
)

@Dao
interface ListEntryDao {
    @Query("SELECT * FROM list_entry WHERE list = :list AND deleted = 0 ORDER BY updatedAt DESC")
    fun observeLive(list: String): Flow<List<ListEntryEntity>>

    /** Includes tombstones: what sync and export need. */
    @Query("SELECT * FROM list_entry WHERE list = :list ORDER BY updatedAt DESC")
    suspend fun all(list: String): List<ListEntryEntity>

    @Query("SELECT * FROM list_entry WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<ListEntryEntity>

    /** The row for a kind and value in any state, so a re-add revives the tombstone and keeps its id. */
    @Query("SELECT * FROM list_entry WHERE list = :list AND kind = :kind AND value = :value LIMIT 1")
    suspend fun find(list: String, kind: String, value: String): ListEntryEntity?

    /** Live rows whose value is the number or one of its prefixes; one indexed query. */
    @Query("SELECT * FROM list_entry WHERE list = :list AND deleted = 0 AND value IN (:keys)")
    suspend fun liveMatching(list: String, keys: List<String>): List<ListEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ListEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ListEntryEntity>)
}

@Database(
    entities = [ListEntryEntity::class, CallDecisionEntity::class],
    version = SpamDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class SpamDatabase : RoomDatabase() {
    abstract fun listEntryDao(): ListEntryDao

    abstract fun callDecisionDao(): CallDecisionDao

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "spam.db"

        fun create(context: Context): SpamDatabase = Room.databaseBuilder<SpamDatabase>(context.applicationContext, FILE_NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()
    }
}
