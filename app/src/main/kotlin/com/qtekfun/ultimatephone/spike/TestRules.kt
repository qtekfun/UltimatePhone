package com.qtekfun.ultimatephone.spike

enum class ScreenAction { WARN, SILENCE, REJECT }

data class TestRule(val digits: String, val action: ScreenAction)

/** Fixed list of numbers the spike reacts to. Matching is by trailing digits, so +34 and local forms agree. */
object TestRules {
    const val MIN_DIGITS = 6

    fun parse(raw: String, action: ScreenAction): TestRule? {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= MIN_DIGITS) TestRule(digits, action) else null
    }

    fun match(rules: List<TestRule>, number: String?): TestRule? {
        val digits = number?.filter { it.isDigit() }.orEmpty()
        if (digits.length < MIN_DIGITS) return null
        return rules.firstOrNull { digits.endsWith(it.digits) || it.digits.endsWith(digits) }
    }

    fun serialize(rules: List<TestRule>): String = rules.joinToString(",") { "${it.digits}:${it.action.name}" }

    fun deserialize(raw: String): List<TestRule> = raw.split(',').mapNotNull { entry ->
        val parts = entry.split(':')
        val action = parts.getOrNull(1)?.let { name -> ScreenAction.entries.firstOrNull { it.name == name } }
        if (parts.size == 2 && action != null) parse(parts[0], action) else null
    }
}
