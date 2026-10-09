package com.qtekfun.ultimatephone.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.qtekfun.ultimatephone.R

/**
 * Shows the crash of the previous run. Deliberately plain framework views, no Hilt and no Compose: it has to work even
 * when the cause of the crash is in the code that builds the normal screens.
 */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReporter.pending(this)
        if (report == null) {
            finish()
            return
        }
        val pad = (PADDING_DP * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(
            TextView(this).apply {
                setText(R.string.crash_title)
                textSize = TITLE_SP
                setTypeface(typeface, Typeface.BOLD)
                isAccessibilityHeading = true
            }
        )
        column.addView(TextView(this).apply { setText(R.string.crash_body) })
        column.addView(
            TextView(this).apply {
                text = report
                typeface = Typeface.MONOSPACE
                textSize = TRACE_SP
                setTextIsSelectable(true)
                setPadding(0, pad, 0, pad)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        column.addView(button(R.string.crash_copy) { copy(report) })
        column.addView(button(R.string.crash_share) { share(report) })
        column.addView(
            button(R.string.crash_close) {
                CrashReporter.clear(this)
                finish()
            }
        )
        val scroll = ScrollView(this).apply { addView(column) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    private fun button(label: Int, onClick: () -> Unit) = Button(this).apply {
        setText(label)
        isAllCaps = false
        minimumHeight = (MIN_TARGET_DP * resources.displayMetrics.density).toInt()
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        visibility = View.VISIBLE
    }

    private fun copy(report: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("UltimatePhone crash", report))
        Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
    }

    private fun share(report: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(send, getString(R.string.crash_share)))
    }

    private companion object {
        const val PADDING_DP = 16
        const val MIN_TARGET_DP = 48
        const val TITLE_SP = 20f
        const val TRACE_SP = 11f
    }
}
