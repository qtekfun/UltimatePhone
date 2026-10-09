package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
    UltimatePhoneTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        Surface { content() }
    }
}

@Composable
private fun SettingsSample() {
    var switch by remember { mutableStateOf(true) }
    var choice by remember { mutableStateOf(ThemeMode.SYSTEM) }
    Column(Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        SettingsGroup(title = "Spam") {
            SettingsRow(title = "Spam filter", summary = "Block, silence or warn", icon = Icons.Filled.Security, onClick = {})
            SettingsRow(title = "Version", summary = null, trailing = { StatusChip("0.1.0") })
        }
        SettingsGroup(title = "Appearance") {
            SwitchRow(title = "Use system colours", summary = "Follow the wallpaper", icon = Icons.Filled.Palette, checked = switch, onCheckedChange = {
                switch =
                    it
            })
            RadioRow(title = "System default", selected = choice == ThemeMode.SYSTEM, onClick = { choice = ThemeMode.SYSTEM })
            RadioRow(title = "Dark", selected = choice == ThemeMode.DARK, onClick = { choice = ThemeMode.DARK })
        }
        SegmentedChoice(options = ThemeMode.entries, selected = choice, onSelected = { choice = it }, label = { it.name })
    }
}

@Composable
private fun BannersSample() {
    Column(Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        BannerKind.entries.forEach { kind ->
            InfoBanner(kind = kind, title = kind.name, body = "A short sentence that says what happened.", action = UiAction("Action") {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            StatusChip("Recording", icon = Icons.Filled.Mic, kind = BannerKind.Error)
            StatusChip("Restaurant", icon = Icons.Filled.Storefront)
            StatusChip("Spam", kind = BannerKind.Warning)
        }
    }
}

@Composable
private fun CallSample() {
    Column(Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            AvatarStyled(name = "Ada Lovelace", size = 56.dp)
            AvatarStyled(name = "5551234567", size = 56.dp)
            AvatarStyled(name = null, size = 56.dp, business = true)
            AvatarStyled(name = "Pizzeria", size = 56.dp, business = true, businessIcon = Icons.Filled.Contacts)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Large)) {
            CallButton(CallButtonKind.Decline, "Decline", onClick = {})
            CallButton(CallButtonKind.Answer, "Answer", onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            CallActionButton(Icons.Filled.Mic, "Mute", onClick = {}, checked = true, stateDescription = "On")
            CallActionButton(Icons.Filled.Mic, "Hold", onClick = {}, checked = false, stateDescription = "Off")
            CallActionButton(Icons.Filled.Contacts, "Add call", onClick = {})
        }
        CallPillButton(CallButtonKind.End, "End call", onClick = {})
    }
}

@Preview(name = "Settings light", showBackground = true)
@Composable
private fun SettingsLightPreview() = Themed(false) { SettingsSample() }

@Preview(name = "Settings dark", showBackground = true)
@Composable
private fun SettingsDarkPreview() = Themed(true) { SettingsSample() }

@Preview(name = "Banners light", showBackground = true)
@Composable
private fun BannersLightPreview() = Themed(false) { BannersSample() }

@Preview(name = "Banners dark", showBackground = true)
@Composable
private fun BannersDarkPreview() = Themed(true) { BannersSample() }

@Preview(name = "Call parts light", showBackground = true)
@Composable
private fun CallLightPreview() = Themed(false) { CallSample() }

@Preview(name = "Call parts dark", showBackground = true)
@Composable
private fun CallDarkPreview() = Themed(true) { CallSample() }

@Preview(name = "Empty state", showBackground = true)
@Composable
private fun EmptyStatePreview() = Themed(false) {
    EmptyState(Icons.Filled.Contacts, "No contacts yet", "Add a contact and it will show up here.", action = UiAction("Add contact") {})
}

@Preview(name = "Screen scaffold", showBackground = true)
@Composable
private fun ScaffoldPreview() = Themed(false) {
    ScreenScaffold(title = "Settings", onBack = {}) { padding ->
        Column(Modifier.padding(padding)) { SettingsSample() }
    }
}
