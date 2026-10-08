package com.qtekfun.ultimatephone.spike

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.qtekfun.ultimatephone.R

/** Phase 0 spike screen: one button per test of docs/spec/05-fase0-spike-oppo.md, and the live event log. */
class MainActivity : ComponentActivity() {
    private lateinit var logView: TextView
    private lateinit var rulesView: TextView
    private lateinit var ruleNumber: EditText
    private lateinit var ruleAction: Spinner
    private lateinit var dialNumber: EditText
    private lateinit var simSpinner: Spinner
    private var sims: List<SimAccount> = emptyList()
    private val logListener: () -> Unit = { logView.text = EventLog.text() }
    private val recorder by lazy { CallRecorder(this) }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        EventLog.add("perm", result.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })
        refreshSims()
    }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        EventLog.add("role", "request finished, resultCode=${it.resultCode}")
        runProbe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = UiKit.column(this)
        addRolesSection(column)
        addScreeningSection(column)
        addDialSection(column)
        addDataSection(column)
        addRecordingSection(column)
        addBackgroundSection(column)
        addLogSection(column)
        val scroll = ScrollView(this).apply { addView(column) }
        UiKit.fitSystemBars(scroll)
        setContentView(scroll)
        intent.data?.takeIf { it.scheme == "tel" }?.let { dialNumber.setText(it.schemeSpecificPart) }
        refreshRules()
        refreshSims()
    }

    override fun onStart() {
        super.onStart()
        EventLog.addListener(logListener)
        logListener()
    }

    override fun onStop() {
        EventLog.removeListener(logListener)
        super.onStop()
    }

    private fun addRolesSection(column: LinearLayout) {
        column.addView(UiKit.heading(this, getString(R.string.section_roles)))
        column.addView(UiKit.button(this, getString(R.string.btn_role_dialer)) { requestRole(RoleManager.ROLE_DIALER) })
        column.addView(UiKit.button(this, getString(R.string.btn_role_screening)) { requestRole(RoleManager.ROLE_CALL_SCREENING) })
        column.addView(UiKit.button(this, getString(R.string.btn_permissions)) { permissions.launch(PERMISSIONS) })
        column.addView(UiKit.button(this, getString(R.string.btn_default_apps)) { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) })
        column.addView(
            UiKit.button(this, getString(R.string.btn_battery)) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        )
        column.addView(UiKit.button(this, getString(R.string.btn_probe)) { runProbe() })
    }

    private fun addScreeningSection(column: LinearLayout) {
        column.addView(UiKit.heading(this, getString(R.string.section_screening)))
        column.addView(UiKit.body(this, getString(R.string.screening_hint)))
        ruleNumber = UiKit.edit(this, getString(R.string.hint_rule_number))
        ruleAction = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, ScreenAction.entries.map { it.name })
        }
        rulesView = UiKit.body(this)
        column.addView(ruleNumber)
        column.addView(ruleAction)
        column.addView(UiKit.button(this, getString(R.string.btn_add_rule)) { addRule() })
        column.addView(UiKit.button(this, getString(R.string.btn_clear_rules)) { clearRules() })
        column.addView(rulesView)
    }

    private fun addDialSection(column: LinearLayout) {
        column.addView(UiKit.heading(this, getString(R.string.section_dial)))
        dialNumber = UiKit.edit(this, getString(R.string.hint_dial_number))
        simSpinner = Spinner(this)
        column.addView(dialNumber)
        column.addView(simSpinner)
        column.addView(UiKit.button(this, getString(R.string.btn_refresh_sims)) { refreshSims() })
        column.addView(UiKit.button(this, getString(R.string.btn_dial)) { dial() })
    }

    private fun addDataSection(column: LinearLayout) {
        column.addView(UiKit.heading(this, getString(R.string.section_data)))
        column.addView(UiKit.button(this, getString(R.string.btn_read_calllog)) { DeviceProbes.readCallLog(this) })
        column.addView(UiKit.button(this, getString(R.string.btn_contacts_count)) { DeviceProbes.countContacts(this) })
        column.addView(UiKit.button(this, getString(R.string.btn_contact_create)) { DeviceProbes.createTestContact(this) })
        column.addView(UiKit.button(this, getString(R.string.btn_contact_delete)) { DeviceProbes.deleteTestContacts(this) })
    }

    private fun addRecordingSection(column: LinearLayout) {
        column.addView(UiKit.heading(this, getString(R.string.section_recording)))
        column.addView(UiKit.button(this, getString(R.string.btn_probe_sources)) { recorder.probeAll { EventLog.add("rec", "source probe finished") } })
    }

    private fun addBackgroundSection(column: LinearLayout) {
        column.addView(UiKit.heading(this, getString(R.string.section_background)))
        column.addView(UiKit.button(this, getString(R.string.btn_bg_daily)) { BackgroundProbe.scheduleDaily(this) })
        column.addView(UiKit.button(this, getString(R.string.btn_bg_fast)) { BackgroundProbe.scheduleFast(this) })
        column.addView(UiKit.button(this, getString(R.string.btn_bg_once)) { BackgroundProbe.runOnce(this) })
        column.addView(UiKit.button(this, getString(R.string.btn_bg_cancel)) { BackgroundProbe.cancelAll(this) })
    }

    private fun addLogSection(column: LinearLayout) {
        column.addView(UiKit.heading(this, getString(R.string.section_log)))
        column.addView(UiKit.button(this, getString(R.string.btn_log_copy)) { copyLog() })
        column.addView(UiKit.button(this, getString(R.string.btn_log_clear)) { EventLog.clear() })
        logView = UiKit.mono(this)
        column.addView(logView)
    }

    private fun requestRole(role: String) {
        val roles = getSystemService(RoleManager::class.java)
        if (!roles.isRoleAvailable(role)) {
            EventLog.add("role", "$role is NOT available on this device")
            return
        }
        if (roles.isRoleHeld(role)) {
            EventLog.add("role", "$role already held")
            return
        }
        roleRequest.launch(roles.createRequestRoleIntent(role))
    }

    private fun runProbe() {
        CapabilityProbe.run(this).forEach { EventLog.add("probe", it) }
    }

    private fun addRule() {
        val action = ScreenAction.entries[ruleAction.selectedItemPosition]
        val rule = TestRules.parse(ruleNumber.text.toString(), action)
        if (rule == null) {
            EventLog.add("rules", "need at least ${TestRules.MIN_DIGITS} digits")
            return
        }
        RuleStore.save(this, RuleStore.load(this).filterNot { it.digits == rule.digits } + rule)
        ruleNumber.text.clear()
        refreshRules()
    }

    private fun clearRules() {
        RuleStore.save(this, emptyList())
        refreshRules()
    }

    private fun refreshRules() {
        val rules = RuleStore.load(this)
        rulesView.text = if (rules.isEmpty()) getString(R.string.no_rules) else rules.joinToString("\n") { "${PhoneMask.mask(it.digits)}  ${it.action}" }
    }

    private fun refreshSims() {
        sims = SimInfo.accounts(this)
        val labels = sims.map { "${it.label} (slot ${it.slot})" }.ifEmpty { listOf(getString(R.string.no_sims)) }
        simSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    @SuppressLint("MissingPermission")
    private fun dial() {
        val number = dialNumber.text.toString().trim()
        if (number.isEmpty()) return
        val account = sims.getOrNull(simSpinner.selectedItemPosition)
        val extras = Bundle()
        if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account.handle)
        EventLog.add("dial", "placing call to ${PhoneMask.mask(number)} via ${account?.label ?: "default SIM"} slot=${account?.slot}")
        try {
            getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), extras)
        } catch (e: SecurityException) {
            EventLog.add("dial", "denied: ${e.message}")
        }
    }

    private fun copyLog() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("spike log", EventLog.text()))
        EventLog.add("log", "copied to clipboard")
    }

    private companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}
