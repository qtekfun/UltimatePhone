package com.qtekfun.ultimatephone.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a system settings screen, trying each candidate of a [ResolvedTopic] in turn. A screen that does not exist on this
 * device, or that the system refuses to open, is skipped; nothing is assumed to be present, and the caller is told when
 * none of them opened.
 */
object SystemSettingsLauncher {
    fun open(context: Context, topic: ResolvedTopic): Boolean {
        for (spec in topic.intents) {
            val intent = build(spec, context.packageName) ?: continue
            if (start(context, intent)) return true
        }
        return false
    }

    fun build(spec: IntentSpec, packageName: String): Intent? {
        if (!spec.isUsable) return null
        val intent = if (spec.action != null) Intent(spec.action) else Intent()
        val pkg = spec.componentPackage
        val cls = spec.componentClass
        if (pkg != null && cls != null) intent.component = ComponentName(pkg, cls)
        spec.dataFor(packageName)?.let { intent.data = Uri.parse(it) }
        spec.extrasFor(packageName).forEach { (key, value) -> intent.putExtra(key, value) }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        // Another app's screen that is not exported to us.
        false
    }
}
