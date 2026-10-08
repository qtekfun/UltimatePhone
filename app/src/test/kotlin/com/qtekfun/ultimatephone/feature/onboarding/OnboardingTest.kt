package com.qtekfun.ultimatephone.feature.onboarding

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    private fun advance(times: Int, from: OnboardingState = OnboardingState()) = (1..times).fold(from) { state, _ -> OnboardingFlow.next(state) }

    @Test
    fun theFlowStartsAtTheWelcomeAndWalksThroughEveryStepInOrder() {
        var state = OnboardingState()
        val seen = mutableListOf(state.step)
        while (!state.isLast) {
            state = OnboardingFlow.next(state)
            seen += state.step
        }
        assertEquals(OnboardingStep.entries, seen)
        assertEquals(OnboardingStep.entries.size, state.total)
        assertEquals(state.total, state.position)
    }

    @Test
    fun continuingMarksTheStepCompletedAndSkippingMarksItSkipped() {
        val afterWelcome = advance(1)
        assertEquals(OnboardingStep.ROLES, afterWelcome.step)
        assertEquals(setOf(OnboardingStep.WELCOME), afterWelcome.completed)

        val skipped = OnboardingFlow.skip(afterWelcome)
        assertEquals(OnboardingStep.PERMISSIONS, skipped.step)
        assertEquals(setOf(OnboardingStep.ROLES), skipped.skipped)
        assertFalse(OnboardingStep.ROLES in skipped.completed)
    }

    @Test
    fun theWelcomeAndTheSummaryCannotBeSkipped() {
        val start = OnboardingState()
        assertEquals(start, OnboardingFlow.skip(start))
        val last = advance(OnboardingStep.entries.size - 1)
        assertTrue(last.isLast)
        assertEquals(last, OnboardingFlow.skip(last))
    }

    @Test
    fun backGoesToThePreviousStepAndStopsAtTheFirst() {
        val state = advance(3)
        assertEquals(OnboardingStep.DATA, state.step)
        assertEquals(OnboardingStep.PERMISSIONS, OnboardingFlow.back(state).step)
        assertEquals(OnboardingState(), OnboardingFlow.back(OnboardingState()))
    }

    @Test
    fun revisitingASkippedStepAndContinuingRemovesItFromTheLaterList() {
        val skipped = OnboardingFlow.skip(advance(1))
        assertEquals(listOf(OnboardingStep.ROLES), OnboardingFlow.pendingForLater(skipped))
        val back = OnboardingFlow.back(skipped)
        val done = OnboardingFlow.next(back)
        assertTrue(OnboardingFlow.pendingForLater(done).isEmpty())
        assertTrue(OnboardingStep.ROLES in done.completed)
    }

    @Test
    fun theLaterListFollowsTheOrderOfTheSteps() {
        var state = advance(1)
        repeat(OnboardingStep.entries.size - 2) { state = OnboardingFlow.skip(state) }
        assertTrue(state.isLast)
        assertEquals(
            listOf(OnboardingStep.ROLES, OnboardingStep.PERMISSIONS, OnboardingStep.DATA, OnboardingStep.NEXTCLOUD, OnboardingStep.BATTERY),
            OnboardingFlow.pendingForLater(state)
        )
    }

    @Test
    fun theWholeFlowCanBeSkippedWithNoAccountAndNoNetwork() {
        // Only the welcome and the summary need a tap; nothing in between is required.
        var state = OnboardingFlow.next(OnboardingState())
        while (!state.isLast) state = OnboardingFlow.skip(state)
        val finished = OnboardingFlow.finish(state)
        assertTrue(finished.finished)
    }

    @Test
    fun finishingIsOnlyPossibleFromTheSummary() {
        assertFalse(OnboardingFlow.finish(advance(2)).finished)
        assertTrue(OnboardingFlow.finish(advance(OnboardingStep.entries.size - 1)).finished)
    }

    @Test
    fun aFinishedFlowIgnoresFurtherMoves() {
        val finished = OnboardingFlow.finish(advance(OnboardingStep.entries.size - 1))
        assertEquals(finished, OnboardingFlow.back(finished))
        assertEquals(finished, OnboardingFlow.next(finished))
    }

    @Test
    fun theStateCanBeRestoredFromSavedStrings() {
        val state = OnboardingFlow.restore("DATA", listOf("ROLES", "BOGUS"), listOf("WELCOME", "PERMISSIONS"))
        assertEquals(OnboardingStep.DATA, state.step)
        assertEquals(setOf(OnboardingStep.ROLES), state.skipped)
        assertEquals(setOf(OnboardingStep.WELCOME, OnboardingStep.PERMISSIONS), state.completed)
        assertEquals(OnboardingStep.WELCOME, OnboardingFlow.restore(null, emptyList(), emptyList()).step)
        assertEquals(OnboardingStep.WELCOME, OnboardingFlow.restore("NOPE", emptyList(), emptyList()).step)
    }

    @Test
    fun permissionGroupsAskOnlyForWhatIsMissing() {
        val granted = setOf("android.permission.READ_CONTACTS")
        val missing = PermissionGroup.CONTACTS.missing { it in granted }
        assertEquals(listOf("android.permission.WRITE_CONTACTS"), missing)
        assertFalse(PermissionGroup.CONTACTS.isGranted { it in granted })
        assertTrue(PermissionGroup.CONTACTS.isGranted { true })
        assertTrue("every permission is declared once", PermissionGroup.entries.flatMap { it.permissions }.let { it.size == it.toSet().size })
    }

    @Test
    fun everyAskedPermissionIsDeclaredInTheManifest() {
        val manifest = File(System.getProperty("user.dir"), "src/main/AndroidManifest.xml").readText()
        PermissionGroup.entries.flatMap { it.permissions }.forEach { permission ->
            assertTrue("$permission must be in AndroidManifest.xml", manifest.contains("android:name=\"$permission\""))
        }
    }
}

class OemGuidesTest {
    private val assetText = File(System.getProperty("user.dir"), "src/main/assets/${OemGuides.ASSET}").readText()
    private val file = OemGuides.parse(assetText)!!

    private fun guideFor(manufacturer: String?, brand: String? = null) = OemGuides.select(file, manufacturer, brand).id

    @Test
    fun theShippedAssetParsesAndHasEveryGuideTheSpecAsksFor() {
        assertEquals(setOf("coloros", "samsung", "xiaomi", "stock", "generic"), file.guides.map { it.id }.toSet())
    }

    @Test
    fun colorosCoversOppoRealmeAndOnePlus() {
        assertEquals("coloros", guideFor("OPPO"))
        assertEquals("coloros", guideFor("realme"))
        assertEquals("coloros", guideFor("OnePlus"))
        assertEquals("coloros", guideFor("unknown", "OPPO"))
    }

    @Test
    fun otherManufacturersGetTheirOwnGuide() {
        assertEquals("samsung", guideFor("samsung"))
        assertEquals("xiaomi", guideFor("Xiaomi"))
        assertEquals("xiaomi", guideFor("Xiaomi", "Redmi"))
        assertEquals("xiaomi", guideFor("Unknown", "POCO"))
        assertEquals("stock", guideFor("Google"))
    }

    @Test
    fun unknownOrMissingManufacturersGetTheGenericGuide() {
        assertEquals("generic", guideFor("Acme Phones"))
        assertEquals("generic", guideFor(null))
        assertEquals("generic", guideFor("", ""))
    }

    @Test
    fun everyGuideResolvesToTheFourTopicsInOrder() {
        file.guides.forEach { guide ->
            val topics = OemGuides.resolve(file, guide)
            assertEquals(guide.id, GuideTopics.all, topics.map { it.topic })
        }
    }

    @Test
    fun aGuideMissingATopicFallsBackToTheGenericOne() {
        val partial = file.copy(guides = listOf(OemGuide("x", listOf("acme"), emptyMap()), file.guides.first { it.id == "generic" }))
        val topics = OemGuides.resolve(partial, partial.guides.first())
        assertEquals("onboarding_oem_generic_battery", topics.first { it.topic == GuideTopics.BATTERY }.textKey)
    }

    @Test
    fun specificScreensComeFirstAndTheGenericOnesAreTheLastResort() {
        val battery = OemGuides.resolve(file, file.guides.first { it.id == "coloros" }).first { it.topic == GuideTopics.BATTERY }
        assertEquals("com.oplus.battery", battery.intents.first().componentPackage)
        assertTrue(battery.intents.any { it.action == "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS" })
        assertTrue("the app's own settings page is always the last fallback", battery.intents.last().action == "android.settings.APPLICATION_DETAILS_SETTINGS")
    }

    @Test
    fun everyIntentSpecInTheAssetIsUsable() {
        file.guides.flatMap { it.topics.values }.flatMap { it.intents }.forEach { spec ->
            assertTrue("$spec", spec.isUsable)
            if (spec.component != null) {
                assertNotNull("$spec", spec.componentPackage)
                assertNotNull("$spec", spec.componentClass)
            }
        }
    }

    @Test
    fun componentsAreParsedAndPlaceholdersAreFilledIn() {
        val spec = IntentSpec(
            component = "com.miui.powerkeeper/com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            data = "package:{package}",
            extras = mapOf("package_name" to "{package}")
        )
        assertEquals("com.miui.powerkeeper", spec.componentPackage)
        assertEquals("com.miui.powerkeeper.ui.HiddenAppsConfigActivity", spec.componentClass)
        assertEquals("package:com.qtekfun.ultimatephone", spec.dataFor("com.qtekfun.ultimatephone"))
        assertEquals(mapOf("package_name" to "com.qtekfun.ultimatephone"), spec.extrasFor("com.qtekfun.ultimatephone"))
    }

    @Test
    fun specsThatCannotStartAnythingAreNotUsable() {
        assertFalse(IntentSpec().isUsable)
        assertFalse(IntentSpec(data = "package:x").isUsable)
        assertFalse(IntentSpec(component = "broken").isUsable)
        assertNull(IntentSpec(component = "broken").componentPackage)
    }

    @Test
    fun damagedOrUnknownSchemaFilesAreRejectedAndTheFallbackStillWorks() {
        assertNull(OemGuides.parse("{ not json"))
        assertNull(OemGuides.parse("""{"schema":99,"guides":[{"id":"generic"}]}"""))
        assertNull(OemGuides.parse("""{"schema":1,"guides":[]}"""))
        val fallback = OemGuides.fallback()
        assertEquals("generic", OemGuides.select(fallback, "OPPO", "OPPO").id)
        assertEquals(4, OemGuides.resolve(fallback, fallback.guides.first()).size)
    }

    @Test
    fun unknownFieldsInTheAssetAreIgnored() {
        val parsed = OemGuides.parse("""{"schema":1,"future":true,"guides":[{"id":"generic","extra":1,"topics":{}}]}""")
        assertNotNull(parsed)
    }

    @Test
    fun everyGuideTextExistsInEnglishAndSpanish() {
        val en = stringNames("values/strings_onboarding.xml")
        val es = stringNames("values-es/strings_onboarding.xml")
        val keys = file.guides.flatMap { it.topics.values }.map { it.textKey }.toSet()
        keys.forEach {
            assertTrue("$it missing in English", it in en)
            assertTrue("$it missing in Spanish", it in es)
        }
        GuideTopics.all.forEach { assertTrue("generic text for $it", "onboarding_oem_generic_$it" in en) }
    }

    private fun stringNames(path: String): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(File(System.getProperty("user.dir"), "src/main/res/$path").readText()).map { it.groupValues[1] }.toSet()
}

class StringsParityTest {
    private fun names(path: String): List<String> =
        Regex("""<string name="([^"]+)"""").findAll(File(System.getProperty("user.dir"), "src/main/res/$path").readText()).map { it.groupValues[1] }.toList()

    @Test
    fun dataStringsExistInBothLanguagesAndUseTheDataPrefix() {
        val en = names("values/strings_data.xml")
        val es = names("values-es/strings_data.xml")
        assertEquals(en.toSet(), es.toSet())
        assertEquals("no duplicates", en.size, en.toSet().size)
        assertTrue(en.all { it.startsWith("data_") })
    }

    @Test
    fun onboardingStringsExistInBothLanguagesAndUseTheOnboardingPrefix() {
        val en = names("values/strings_onboarding.xml")
        val es = names("values-es/strings_onboarding.xml")
        assertEquals(en.toSet(), es.toSet())
        assertEquals("no duplicates", en.size, en.toSet().size)
        assertTrue(en.all { it.startsWith("onboarding_") })
    }

    @Test
    fun placeholdersMatchBetweenLanguages() {
        listOf("strings_data.xml", "strings_onboarding.xml").forEach { file ->
            val en = placeholders("values/$file")
            val es = placeholders("values-es/$file")
            en.forEach { (name, holders) -> assertEquals("$name placeholders", holders, es[name]) }
        }
    }

    private fun placeholders(path: String): Map<String, List<String>> {
        val text = File(System.getProperty("user.dir"), "src/main/res/$path").readText()
        val holder = Regex("""%\d\$[sd]""")
        return Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(text).associate { match ->
            match.groupValues[1] to holder.findAll(match.groupValues[2]).map { it.value }.sorted().toList()
        }
    }
}
