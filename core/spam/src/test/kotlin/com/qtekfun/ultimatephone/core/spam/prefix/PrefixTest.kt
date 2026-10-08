package com.qtekfun.ultimatephone.core.spam.prefix

import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrefixTest {
    @Test
    fun candidatesAreProperPrefixesLongestFirst() {
        assertEquals(listOf("+3461", "+346", "+34", "+3"), PrefixCandidates.of("+34612").dropWhile { it.length > 5 })
        val all = PrefixCandidates.of("+34612345678")
        assertEquals("+3461234567", all.first())
        assertEquals("+3", all.last())
        assertEquals(10, all.size)
        assertTrue("+34612345678" !in all)
    }

    @Test
    fun candidatesOfTinyInputs() {
        assertEquals(emptyList<String>(), PrefixCandidates.of(""))
        assertEquals(emptyList<String>(), PrefixCandidates.of("+"))
        assertEquals(emptyList<String>(), PrefixCandidates.of("+3"))
        assertEquals(listOf("+3"), PrefixCandidates.of("+34"))
    }

    private fun rules(vararg r: PrefixRule) = PrefixRuleSet(r.toList())

    @Test
    fun matchesPrefixAtTheStartOfTheNationalNumber() {
        val set = rules(PrefixRule("es-400", "ES", "400"))
        assertEquals("es-400", set.match("+34400123456", "ES")?.ruleId)
        assertEquals("es-400", set.match("+34400123456", null)?.ruleId)
    }

    @Test
    fun boundariesDoNotMatchNeighbouringPrefixes() {
        val set = rules(PrefixRule("es-400", "ES", "400"))
        assertNull(set.match("+34401123456", "ES"))
        assertNull(set.match("+34399123456", "ES"))
        assertNull(set.match("+34040012345", "ES"))
        assertNull(set.match("+3440", "ES"))
    }

    @Test
    fun longestPrefixWins() {
        val set = rules(
            PrefixRule("short", "ES", "90"),
            PrefixRule("long", "ES", "902"),
            PrefixRule("longest", "ES", "9021")
        )
        assertEquals("longest", set.match("+34902123456", "ES")?.ruleId)
        assertEquals("long", set.match("+34902223456", "ES")?.ruleId)
        assertEquals("short", set.match("+34901223456", "ES")?.ruleId)
    }

    @Test
    fun countryScopingKeepsRulesOfOtherCountriesOut() {
        val set = rules(PrefixRule("us-800", "US", "800"), PrefixRule("ca-800", "CA", "800"))
        assertEquals("us-800", set.match("+18005551234", "US")?.ruleId)
        assertEquals("ca-800", set.match("+18005551234", "CA")?.ruleId)
        assertNull(set.match("+18005551234", "MX"))
    }

    @Test
    fun countryRuleDoesNotApplyToAnotherCallingCode() {
        val set = rules(PrefixRule("es-400", "ES", "400"))
        assertNull(set.match("+49400123456", "DE"))
        assertNull(set.match("+44400123456", "GB"))
    }

    @Test
    fun internationalPrefixRulesNeedNoCountry() {
        val set = rules(PrefixRule("intl", null, "+34900"))
        assertEquals("intl", set.match("+34900123456", "ES")?.ruleId)
        assertEquals("intl", set.match("+34900123456", null)?.ruleId)
    }

    @Test
    fun malformedRulesAreDroppedNotFatal() {
        val set = rules(
            PrefixRule("no-digits", "ES", "4x0"),
            PrefixRule("bad-country", "ZZ", "400"),
            PrefixRule("no-plus", null, "34900"),
            PrefixRule("empty", "ES", ""),
            PrefixRule("ok", "ES", "400")
        )
        assertEquals(1, set.size)
    }

    @Test
    fun matchCarriesLevelInformationalFlagAndSource() {
        val m = rules(PrefixRule("r", "ES", "400", SpamLevel.RULE, true, "BOE-A-2026-8409")).match("+34400123456", "ES")!!
        assertEquals(SpamLevel.RULE, m.level)
        assertTrue(m.informational)
        assertEquals("BOE-A-2026-8409", m.source)
    }

    @Test
    fun emptySetMatchesNothing() {
        assertNull(rules().match("+34400123456", "ES"))
    }

    // Rules file.

    @Test
    fun builtInRulesContainTheSpanish400AsInformationalCommercialCall() {
        val file = BuiltInRules.load()
        assertEquals(1, file.schema)
        assertTrue(file.version.isNotBlank())
        val rule = file.rules.single { it.id == "es-400-commercial" }
        assertEquals("ES", rule.country)
        assertEquals("400", rule.prefix)
        assertEquals(SpamLevel.RULE, rule.level)
        assertTrue(rule.informational)
        assertEquals("BOE-A-2026-8409", rule.source)
        assertEquals("es-400-commercial", PrefixRuleSet(file.rules).match("+34400987654", "ES")?.ruleId)
    }

    @Test
    fun parserIgnoresUnknownFieldsAndSkipsBadRules() {
        val file = PrefixRulesParser.parse(
            """
            {"schema":1,"version":"v2","extra":true,"rules":[
              {"id":"a","country":"es","prefix":"400","level":"rule","informational":true,"future":1},
              {"id":"b","prefix":"400","level":"RULE"},
              {"id":"c","country":"ES","prefix":"400","level":"BOGUS"},
              {"country":"ES","prefix":"400","level":"RULE"},
              "nonsense",
              {"id":"d","prefix":"+34900","level":"COMMUNITY"}
            ]}
            """.trimIndent()
        )
        assertEquals(listOf("a", "d"), file.rules.map { it.id })
        assertEquals(4, file.skipped)
        assertEquals("ES", file.rules[0].country)
        assertEquals(SpamLevel.COMMUNITY, file.rules[1].level)
    }

    @Test
    fun parserRejectsBrokenFilesAndNewerSchemas() {
        listOf(
            "not json",
            "[]",
            """{"version":"x","rules":[]}""",
            """{"schema":1,"rules":[]}""",
            """{"schema":1,"version":"x"}""",
            """{"schema":2,"version":"x","rules":[]}""",
            """{"schema":0,"version":"x","rules":[]}"""
        ).forEach {
            try {
                PrefixRulesParser.parse(it)
                fail("should reject: $it")
            } catch (_: RulesFormatException) {
                // expected
            }
        }
    }
}
