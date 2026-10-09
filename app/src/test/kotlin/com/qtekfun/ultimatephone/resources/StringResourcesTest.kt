package com.qtekfun.ultimatephone.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Keeps English (`values`) and Spanish (`values-es`) in lockstep. It reads the XML straight from
 * the source tree, so it needs no Android runtime.
 */
class StringResourcesTest {
    private sealed interface Entry {
        data class Text(val value: String) : Entry
        data class Plural(val items: Map<String, String>) : Entry
        data class StringArray(val items: List<String>) : Entry
    }

    private val resDirs: List<File> = listOf(
        File("src/main/res"),
        File("../core/telecom/src/main/res"),
        File("../core/designsystem/src/main/res")
    ).filter { it.isDirectory }

    private fun load(qualifier: String): Map<String, Entry> {
        val out = linkedMapOf<String, Entry>()
        for (res in resDirs) {
            val dir = File(res, qualifier)
            dir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }
                ?.sortedBy { it.name }
                ?.forEach { parseInto(it, out) }
        }
        return out
    }

    private fun parseInto(file: File, out: MutableMap<String, Entry>) {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val nodes = root.childNodes
        val translatable = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.getAttribute("translatable") != "false" }
        for (el in translatable) {
            val entry: Entry? = when (el.tagName) {
                "string" -> Entry.Text(el.textContent)
                "plurals" -> Entry.Plural(children(el, "item").associate { it.getAttribute("quantity") to it.textContent })
                "string-array" -> Entry.StringArray(children(el, "item").map { it.textContent })
                else -> null
            }
            if (entry != null) {
                val name = el.getAttribute("name")
                assertTrue("Duplicate resource $name in ${file.name}", out.put(name, entry) == null)
            }
        }
    }

    private fun children(el: Element, tag: String): List<Element> =
        (0 until el.childNodes.length).mapNotNull { el.childNodes.item(it) as? Element }.filter { it.tagName == tag }

    private val en by lazy { load("values") }
    private val es by lazy { load("values-es") }

    @Test
    fun resourceDirectoriesAreFound() {
        assertTrue("res dirs not found from ${File(".").absolutePath}", resDirs.size == 3)
        assertTrue(en.isNotEmpty())
    }

    @Test
    fun bothLanguagesHaveTheSameNames() {
        val missingEs = en.keys - es.keys
        val missingEn = es.keys - en.keys
        assertTrue("Missing in values-es: $missingEs", missingEs.isEmpty())
        assertTrue("Missing in values: $missingEn", missingEn.isEmpty())
    }

    @Test
    fun everyEnglishFileHasASpanishCounterpart() {
        val missing = resDirs.flatMap { res ->
            File(res, "values").listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty()
                .filter { !File(res, "values-es/${it.name}").isFile }
                .map { it.path }
        }
        assertTrue("No values-es counterpart for: $missing", missing.isEmpty())
    }

    @Test
    fun localeConfigIsGeneratedForEnglishAndSpanishOnly() {
        // The app declares its languages from the resource folders (generateLocaleConfig), English being the default.
        val gradle = File("build.gradle.kts").readText()
        assertTrue("generateLocaleConfig must be on", Regex("""generateLocaleConfig\s*=\s*true""").containsMatchIn(gradle))
        val props = File("src/main/res/resources.properties").readText()
        assertTrue("default locale must be English", Regex("""unqualifiedResLocale\s*=\s*en\b""").containsMatchIn(props))
        val languages = resDirs.flatMap { res -> res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty().map { it.name } }
            .filter { Regex("values-[a-z]{2}(-r[A-Z]{2})?").matches(it) }
            .toSet()
        assertTrue("Unexpected language folders (add the translation checks first): $languages", languages == setOf("values-es"))
    }

    @Test
    fun resourceKindsMatch() {
        val bad = en.keys.intersect(es.keys).filter { en[it]!!::class != es[it]!!::class }
        assertTrue("Different resource kinds: $bad", bad.isEmpty())
    }

    @Test
    fun placeholdersMatch() {
        // Plurals are checked on their own, per quantity, in pluralPlaceholdersMatchBetweenLanguages.
        val names = en.keys.intersect(es.keys).filter { en[it] !is Entry.Plural }
        val bad = names.flatMap { name ->
            val a = flatten(en.getValue(name))
            val b = flatten(es.getValue(name))
            if (a.keys != b.keys) {
                listOf("$name: item structure differs")
            } else {
                a.keys.filter { placeholders(a.getValue(it)) != placeholders(b.getValue(it)) }
                    .map { "$name[$it]: ${placeholders(a.getValue(it))} vs ${placeholders(b.getValue(it))}" }
            }
        }
        assertTrue("Placeholder mismatch:\n${bad.joinToString("\n")}", bad.isEmpty())
    }

    private fun pluralPairs(): List<Triple<String, Entry.Plural, Entry.Plural>> = en.mapNotNull { (name, e) ->
        val enP = e as? Entry.Plural
        val esP = es[name] as? Entry.Plural
        if (enP != null && esP != null) Triple(name, enP, esP) else null
    }

    @Test
    fun pluralsDefineOneAndOther() {
        val required = setOf("one", "other")
        val bad = pluralPairs().flatMap { (name, enP, esP) ->
            listOfNotNull(
                "$name (en) needs one+other".takeIf { !enP.items.keys.containsAll(required) },
                "$name (es) needs one+other".takeIf { !esP.items.keys.containsAll(required) }
            )
        }
        assertTrue("Plural problems: $bad", bad.isEmpty())
    }

    @Test
    fun pluralPlaceholdersMatchBetweenLanguages() {
        val bad = pluralPairs().mapNotNull { (name, enP, esP) ->
            val enOther = placeholders(enP.items.getValue("other"))
            val esOther = placeholders(esP.items.getValue("other"))
            "$name: other en=$enOther es=$esOther".takeIf { enOther != esOther }
        }
        assertTrue("Plural placeholder problems: $bad", bad.isEmpty())
    }

    @Test
    fun arraysHaveTheSameSize() {
        val bad = en.keys.intersect(es.keys).filter {
            val a = en[it] as? Entry.StringArray
            val b = es[it] as? Entry.StringArray
            a != null && b != null && a.items.size != b.items.size
        }
        assertTrue("Arrays differ in size: $bad", bad.isEmpty())
    }

    @Test
    fun nothingIsLeftUntranslated() {
        val untranslated = en.keys.intersect(es.keys).filter { name ->
            name !in IDENTICAL_ALLOWED && flatten(en.getValue(name)) == flatten(es.getValue(name))
        }
        assertTrue(
            "Identical in both languages (translate it or add to IDENTICAL_ALLOWED): $untranslated",
            untranslated.isEmpty()
        )
    }

    @Test
    fun allowListHasNoStaleEntries() {
        val stale = IDENTICAL_ALLOWED.filter { it !in en.keys }
        assertTrue("IDENTICAL_ALLOWED names that no longer exist: $stale", stale.isEmpty())
    }

    @Test
    fun noBlankStringsOrThreeDots() {
        val bad = mutableListOf<String>()
        for ((prefix, map) in listOf("en:" to en, "es:" to es)) {
            for ((name, e) in map) {
                flatten(e).values.forEach {
                    if (it.isBlank()) bad += "$prefix$name is blank"
                    if (it.contains("...")) bad += "$prefix$name uses three dots instead of the ellipsis character"
                }
            }
        }
        assertTrue("Style problems: $bad", bad.isEmpty())
    }

    @Test
    fun spanishUsesTheInformalForm() {
        // Consistency rule: the Spanish UI addresses the user as "tú", never "usted".
        val formal = Regex("\\b(usted|ustedes)\\b", RegexOption.IGNORE_CASE)
        val bad = es.filter { (_, e) -> flatten(e).values.any { formal.containsMatchIn(it) } }.keys
        assertTrue("Formal address in Spanish: $bad", bad.isEmpty())
    }

    private fun flatten(e: Entry): Map<String, String> = when (e) {
        is Entry.Text -> mapOf("" to e.value)
        is Entry.Plural -> e.items
        is Entry.StringArray -> e.items.mapIndexed { i, s -> i.toString() to s }.toMap()
    }

    private fun placeholders(s: String): List<String> = PLACEHOLDER.findAll(s).map { normalize(it.value) }.filter { it != "%%" }.sorted().toList()

    /** `%s` and `%1$s` mean the same when there is a single argument; keep the position out of the comparison. */
    private fun normalize(p: String): String = p.replace(Regex("^%\\d+\\$"), "%")

    private companion object {
        val PLACEHOLDER = Regex("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]")

        /** Brand names, technical terms and symbols that are legitimately the same in both languages. */
        val IDENTICAL_ALLOWED = setOf(
            "app_name",
            "data_pack_action",
            "recording_action_for",
            "contact_account_format",
            "data_format_csv",
            "data_format_jsonl",
            "onboarding_guide_coloros",
            "onboarding_guide_samsung",
            "onboarding_guide_xiaomi",
            "onboarding_step_name_nextcloud",
            "dialer_sim_slot",
            "incall_audio",
            "incall_route_bluetooth",
            "recents_sim_slot",
            "recording_item_subtitle",
            "recording_item_subtitle_no_duration",
            "spam_filter_chip",
            "spam_badge",
            "spam_badge_reason"
        )
    }
}
