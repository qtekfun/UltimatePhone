package com.qtekfun.ultimatephone.core.settings

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private val FAST = KdfParams.FOR_TESTS

private fun fail(block: () -> Unit): BackupFailure {
    try {
        block()
    } catch (e: BackupException) {
        return e.failure
    }
    fail("expected a BackupException")
    error("unreachable")
}

class BackupCryptoTest {
    private val password = "correct horse battery staple".toCharArray()
    private val plain = "settings payload ñ ✓ \u0000 binary-ish".toByteArray()

    @Test
    fun roundTrip() {
        val file = BackupCrypto.encrypt(plain, password, FAST)
        assertArrayEquals(plain, BackupCrypto.decrypt(file, password))
    }

    @Test
    fun roundTripWithTheDefaultCost() {
        val file = BackupCrypto.encrypt(plain, password)
        assertArrayEquals(plain, BackupCrypto.decrypt(file, password))
    }

    @Test
    fun theSamePasswordInDifferentUnicodeFormsOpensTheFile() {
        val file = BackupCrypto.encrypt(plain, "café".toCharArray(), FAST)
        assertArrayEquals(plain, BackupCrypto.decrypt(file, "café".toCharArray()))
    }

    @Test
    fun wrongPasswordFailsAndReturnsNothing() {
        val file = BackupCrypto.encrypt(plain, password, FAST)
        assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, fail { BackupCrypto.decrypt(file, "wrong".toCharArray()) })
        assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, fail { BackupCrypto.decrypt(file, charArrayOf()) })
    }

    @Test
    fun sameInputGivesDifferentCiphertextEveryTime() {
        val a = BackupCrypto.encrypt(plain, password, FAST)
        val b = BackupCrypto.encrypt(plain, password, FAST)
        assertEquals(a.size, b.size)
        assertFalse(a.contentEquals(b))
        // Salt and nonce are the random parts; both change.
        val header = a.size - plain.size - 16
        assertFalse(a.copyOfRange(20, header).contentEquals(b.copyOfRange(20, header)))
    }

    @Test
    fun flippingAnySingleBitAnywhereFailsAuthentication() {
        val file = BackupCrypto.encrypt(plain, password, FAST)
        for (index in file.indices) {
            for (bit in listOf(0, 7)) {
                val copy = file.copyOf().also { it[index] = (it[index].toInt() xor (1 shl bit)).toByte() }
                fail { BackupCrypto.decrypt(copy, password) }
            }
        }
    }

    @Test
    fun everyTruncationFails() {
        val file = BackupCrypto.encrypt(plain, password, FAST)
        for (length in 0 until file.size) {
            fail { BackupCrypto.decrypt(file.copyOf(length), password) }
        }
    }

    @Test
    fun appendedBytesFail() {
        val file = BackupCrypto.encrypt(plain, password, FAST)
        assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, fail { BackupCrypto.decrypt(file + byteArrayOf(0), password) })
    }

    @Test
    fun aNewerVersionIsReportedAsUnsupportedNotAsCorrupt() {
        val file = BackupCrypto.encrypt(plain, password, FAST).also { it[4] = 2 }
        assertEquals(BackupFailure.UNSUPPORTED_VERSION, fail { BackupCrypto.decrypt(file, password) })
    }

    @Test
    fun notABackupIsRecognisedByTheMagic() {
        assertEquals(BackupFailure.NOT_A_BACKUP, fail { BackupCrypto.decrypt("hello world, not a backup".toByteArray(), password) })
        assertEquals(BackupFailure.NOT_A_BACKUP, fail { BackupCrypto.decrypt(ByteArray(0), password) })
    }

    @Test
    fun headerCostCannotBeUsedToExhaustMemoryOrTime() {
        val file = BackupCrypto.encrypt(plain, password, FAST)
        val hugeMemory = file.copyOf().also { it[6] = 0x7f }
        val hugeIterations = file.copyOf().also { it[10] = 0x7f }
        val tooManyLanes = file.copyOf().also { it[14] = 100.toByte() }
        for (bad in listOf(hugeMemory, hugeIterations, tooManyLanes)) {
            assertEquals(BackupFailure.CORRUPT, fail { BackupCrypto.decrypt(bad, password) })
        }
    }

    @Test
    fun theKdfParametersAreAuthenticated() {
        // A cost that is still in range but not the one used to encrypt: key differs and the tag fails.
        val file = BackupCrypto.encrypt(plain, password, KdfParams(64, 2, 1))
        val lowered = file.copyOf().also { it[13] = 1 }
        assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, fail { BackupCrypto.decrypt(lowered, password) })
    }

    @Test
    fun largePayload() {
        val big = ByteArray(8 * 1024 * 1024) { (it * 31).toByte() }
        val file = BackupCrypto.encrypt(big, password, FAST)
        assertArrayEquals(big, BackupCrypto.decrypt(file, password))
        file[file.size / 2] = (file[file.size / 2].toInt() xor 1).toByte()
        fail { BackupCrypto.decrypt(file, password) }
    }

    @Test
    fun theFileDoesNotContainThePlaintext() {
        val secret = "fake-app-password-do-not-use"
        val file = BackupCrypto.encrypt(secret.toByteArray(), password, FAST)
        assertFalse(String(file, Charsets.ISO_8859_1).contains(secret))
    }
}

private class FakeSection(override val id: String, var value: String, private val valid: (String) -> Boolean = { true }) : ExportSection {
    var restores = 0
    var failRestore = false

    override suspend fun export() = value

    override fun validate(data: String) {
        require(valid(data)) { "invalid" }
    }

    override suspend fun restore(data: String) {
        restores++
        if (failRestore) error("boom")
        value = data
    }
}

class BackupServiceTest {
    private val password = "pw-for-tests".toCharArray()
    private fun service(vararg sections: ExportSection) = BackupService(sections.toList(), clock = { 42L }, kdf = FAST)

    @Test
    fun exportThenImportRestoresEverything() = runTest {
        val source = listOf(FakeSection("settings", "{theme:dark}"), FakeSection("nextcloud", "https://c.example|alice|FAKE-SECRET"))
        val file = service(*source.toTypedArray()).export(password)
        val target = listOf(FakeSection("settings", ""), FakeSection("nextcloud", ""))
        val svc = service(*target.toTypedArray())
        val opened = svc.open(file, password)
        assertEquals(42L, opened.createdAt)
        assertEquals(setOf("settings", "nextcloud"), opened.sectionIds)
        val report = svc.restore(opened, opened.sectionIds)
        assertEquals(setOf("settings", "nextcloud"), report.restored.toSet())
        assertEquals("{theme:dark}", target[0].value)
        assertEquals("https://c.example|alice|FAKE-SECRET", target[1].value)
    }

    @Test
    fun onlyTheChosenSectionsAreRestored() = runTest {
        val file = service(FakeSection("a", "1"), FakeSection("b", "2"), FakeSection("c", "3")).export(password)
        val a = FakeSection("a", "-")
        val b = FakeSection("b", "-")
        val c = FakeSection("c", "-")
        val svc = service(a, b, c)
        val report = svc.restore(svc.open(file, password), setOf("b"))
        assertEquals(listOf("b"), report.restored)
        assertEquals("-", a.value)
        assertEquals("2", b.value)
        assertEquals("-", c.value)
    }

    @Test
    fun onlyChosenSectionsAreWrittenToTheFile() = runTest {
        val svc = service(FakeSection("a", "1"), FakeSection("b", "SECRET"))
        val opened = svc.open(svc.export(password, selected = setOf("a")), password)
        assertEquals(setOf("a"), opened.sectionIds)
    }

    @Test
    fun wrongPasswordOrAlteredFileAppliesNothing() = runTest {
        val file = service(FakeSection("a", "1")).export(password)
        val target = FakeSection("a", "original")
        val svc = service(target)
        assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, fail { svc.open(file, "nope".toCharArray()) })
        val altered = file.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertEquals(BackupFailure.WRONG_PASSWORD_OR_ALTERED, fail { svc.open(altered, password) })
        assertEquals(0, target.restores)
        assertEquals("original", target.value)
    }

    @Test
    fun anInvalidSectionStopsTheWholeRestoreBeforeAnythingIsApplied() = runTest {
        val file = service(FakeSection("a", "1"), FakeSection("b", "bad")).export(password)
        val a = FakeSection("a", "original")
        val b = FakeSection("b", "original") { it != "bad" }
        val svc = service(a, b)
        val opened = svc.open(file, password)
        val failure = try {
            svc.restore(opened, opened.sectionIds)
            null
        } catch (e: BackupException) {
            e.failure
        }
        assertEquals(BackupFailure.INVALID_PAYLOAD, failure)
        assertEquals(0, a.restores)
        assertEquals("original", a.value)
    }

    @Test
    fun aSectionFailingWhileApplyingIsReportedAndTheOthersStillRun() = runTest {
        val file = service(FakeSection("a", "1"), FakeSection("b", "2")).export(password)
        val a = FakeSection("a", "-").also { it.failRestore = true }
        val b = FakeSection("b", "-")
        val svc = service(a, b)
        val report = svc.restore(svc.open(file, password), setOf("a", "b"))
        assertEquals(listOf("b"), report.restored)
        assertEquals(setOf("a"), report.failed.keys)
        assertEquals("2", b.value)
    }

    @Test
    fun sectionsThisVersionDoesNotKnowAreReportedAndIgnored() = runTest {
        val file = service(FakeSection("a", "1"), FakeSection("future", "x")).export(password)
        val a = FakeSection("a", "-")
        val svc = service(a)
        val opened = svc.open(file, password)
        val report = svc.restore(opened, opened.sectionIds)
        assertEquals(listOf("future"), report.unknown)
        assertEquals(listOf("a"), report.restored)
    }

    @Test
    fun payloadsThatDecryptButAreNotBackupsAreRejected() {
        for (junk in listOf("[]", "{}", "{\"format\":1}", "{\"format\":1,\"sections\":{\"a\":5}}", "not json", "{\"format\":9,\"sections\":{}}")) {
            val file = BackupCrypto.encrypt(junk.toByteArray(), password, FAST)
            val failure = fail { service().open(file, password) }
            assertTrue(junk, failure == BackupFailure.INVALID_PAYLOAD || failure == BackupFailure.UNSUPPORTED_VERSION)
        }
    }

    @Test
    fun secretsNeverAppearInTheFile() = runTest {
        val file = service(FakeSection("nextcloud", "FAKE-APP-PASSWORD-123")).export(password)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("FAKE-APP-PASSWORD"))
    }

    @Test
    fun largeSections() = runTest {
        val big = "+34600000000\n".repeat(400_000)
        val file = service(FakeSection("list", big)).export(password)
        val target = FakeSection("list", "")
        val svc = service(target)
        svc.restore(svc.open(file, password), setOf("list"))
        assertEquals(big, target.value)
    }
}
