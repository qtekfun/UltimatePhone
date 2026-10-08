package com.qtekfun.ultimatephone.spike

import android.content.Context
import androidx.core.content.edit

object RuleStore {
    private const val PREFS = "spike"
    private const val KEY = "rules"

    fun load(context: Context): List<TestRule> = TestRules.deserialize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty())

    fun save(context: Context, rules: List<TestRule>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, TestRules.serialize(rules)) }
    }
}
