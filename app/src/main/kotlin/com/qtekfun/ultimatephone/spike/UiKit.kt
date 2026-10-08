package com.qtekfun.ultimatephone.spike

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Plain framework views: the spike has no design on purpose. */
object UiKit {
    private const val PAD_DP = 12

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, PAD_DP), dp(context, PAD_DP), dp(context, PAD_DP), dp(context, PAD_DP))
    }

    fun heading(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(context, PAD_DP), 0, dp(context, 4))
    }

    fun body(context: Context, text: String = ""): TextView = TextView(context).apply { this.text = text }

    fun mono(context: Context): TextView = TextView(context).apply {
        typeface = Typeface.MONOSPACE
        textSize = 11f
        setTextIsSelectable(true)
    }

    fun button(context: Context, text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    fun edit(context: Context, hint: String): EditText = EditText(context).apply {
        this.hint = hint
        setSingleLine()
    }

    /** Edge-to-edge is enforced on current targets: keep the content clear of the system bars and the keyboard. */
    fun fitSystemBars(view: View) {
        view.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
