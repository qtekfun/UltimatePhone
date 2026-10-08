package com.qtekfun.ultimatephone.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TestRulesTest {
    private val rules = listOf(TestRule("600123456", ScreenAction.REJECT), TestRule("34911222333", ScreenAction.SILENCE))

    @Test
    fun matchesInternationalAndLocalForms() {
        assertEquals(ScreenAction.REJECT, TestRules.match(rules, "+34600123456")?.action)
        assertEquals(ScreenAction.REJECT, TestRules.match(rules, "600 123 456")?.action)
        assertEquals(ScreenAction.SILENCE, TestRules.match(rules, "911222333")?.action)
    }

    @Test
    fun unknownOrHiddenNumbersNeverMatch() {
        assertNull(TestRules.match(rules, "+34600999999"))
        assertNull(TestRules.match(rules, null))
        assertNull(TestRules.match(rules, "123"))
    }

    @Test
    fun rejectsRulesThatAreTooShort() {
        assertNull(TestRules.parse("12345", ScreenAction.WARN))
        assertNotNull(TestRules.parse("123456", ScreenAction.WARN))
    }

    @Test
    fun survivesSerialisation() {
        assertEquals(rules, TestRules.deserialize(TestRules.serialize(rules)))
        assertEquals(emptyList<TestRule>(), TestRules.deserialize(""))
        assertEquals(emptyList<TestRule>(), TestRules.deserialize("garbage,1:NOPE"))
    }
}
