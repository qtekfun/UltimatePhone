package com.qtekfun.ultimatephone.core.designsystem

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat

/**
 * Shows [content] once every permission is granted; until then explains why and asks, in context, with an [EmptyState] layout
 * (tonal [icon], [title], the [rationale] and a button).
 */
@Composable
fun PermissionGate(
    permissions: List<String>,
    rationale: String,
    buttonLabel: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Lock,
    title: String = stringResource(R.string.design2_permission_title),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    fun granted() = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var allGranted by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allGranted = granted() }
    if (allGranted) {
        content()
    } else {
        Box(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = icon,
                title = title,
                body = rationale,
                action = UiAction(buttonLabel) { launcher.launch(permissions.toTypedArray()) }
            )
        }
    }
}
