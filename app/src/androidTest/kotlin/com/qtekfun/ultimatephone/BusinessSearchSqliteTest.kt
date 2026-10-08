package com.qtekfun.ultimatephone

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatephone.data.SqliteBusinessNameSearch
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the app's real business name search against the platform SQLite of the emulator. A first version of the packs used an
 * FTS5 index and the Android build answered `no such module: fts5`; the packs now carry the FTS4 index created here.
 */
@RunWith(AndroidJUnit4::class)
class BusinessSearchSqliteTest {
    private fun pack(name: String): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "$name.db").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE numbers (e164 TEXT PRIMARY KEY, name TEXT NOT NULL, brand TEXT, category TEXT, osm_id TEXT, alt_names TEXT)")
            db.execSQL("INSERT INTO numbers (e164, name, category) VALUES ('+34910000001', 'Farmacia Luna', 'pharmacy')")
            db.execSQL("INSERT INTO numbers (e164, name, category) VALUES ('+34910000002', 'Café Central', 'cafe')")
            db.execSQL("INSERT INTO numbers (e164, name, category) VALUES ('+34910000003', 'Mercado Farinelli', 'food')")
            // The same DDL as the data pipeline (pipeline/businesses.py).
            db.execSQL("CREATE VIRTUAL TABLE numbers_fts USING fts4(content=\"numbers\", name, tokenize=unicode61 \"remove_diacritics=1\")")
            db.execSQL("INSERT INTO numbers_fts(numbers_fts) VALUES('rebuild')")
            db.execSQL("INSERT INTO numbers_fts(numbers_fts) VALUES('optimize')")
        }
        return file
    }

    @Test
    fun theKeypadCodeForFarFindsThePharmacyAndTheOtherWordMatch() {
        val search = SqliteBusinessNameSearch(listOf("businesses-test" to pack("fts4-far")))
        val hits = runBlocking { search.searchT9("327", limit = 10) }
        val names = hits.map { it.name }
        assertTrue("Farmacia Luna must be found, got $names", "Farmacia Luna" in names)
        assertTrue("Mercado Farinelli starts a word with far", "Mercado Farinelli" in names)
        assertEquals(0, names.count { it == "Café Central" })
        search.close()
    }

    @Test
    fun accentsDoNotGetInTheWay() {
        val search = SqliteBusinessNameSearch(listOf("businesses-test" to pack("fts4-cafe")))
        val names = runBlocking { search.searchT9("2233", limit = 10) }.map { it.name }
        assertTrue("Café Central must match the keys c-a-f-e, got $names", "Café Central" in names)
        search.close()
    }
}
