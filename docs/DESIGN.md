# UltimatePhone design system

Everything below lives in `:core:designsystem` (`com.qtekfun.ultimatephone.core.designsystem`). Screens never hard-code colours,
corner radii, spacing or text sizes: they use the theme, the tokens and the components listed here. Accessibility rules are in
[ACCESSIBILITY.md](ACCESSIBILITY.md) and are already built into the components; keep them when you add a new one.

## 1. Why a new look

The first betas used the stock Material 3 look: no brand colour (it only followed the wallpaper), long flat lists separated by thin
dividers, settings that read like a form dump, a "#" in the avatar of unknown numbers, grey cards, and a call screen with little
personality. The redesign gives the app an identity (deep teal and mint, like the icon), grouped rounded containers, a clear type
hierarchy and one set of reusable parts.

## 2. Colour

The **brand scheme is the default**. "Use system colours" (Settings > Appearance, default off, `UserSettings.useSystemColors`)
switches to Material You colours instead; light/dark/system mode works with both. `UltimatePhoneTheme(mode, useSystemColors)` builds
the scheme, the shapes, the typography and the expressive motion scheme.

The tonal values were generated from a teal seed (hue about 195) in CIE L*C*h with tone = L*, which is what Material's HCT tone
is, and then checked by `BrandColorsTest` (every text pair is at least 4.5:1, outlines and icons at least 3:1).

| Role | Light | Dark | Use |
|---|---|---|---|
| `primary` / `onPrimary` | `#006C6C` / `#FFFFFF` | `#66D8D6` / `#003C3C` | main actions, selected states, section headers |
| `primaryContainer` / `on` | `#B1EEEB` / `#002222` | `#0B3D3A` (the icon teal) / `#B1EEEB` | hero surfaces, empty-state icon, call wash |
| `secondary` / `onSecondary` | `#446465` / `#FFFFFF` | `#AACDCE` / `#163536` | less prominent actions |
| `secondaryContainer` / `on` | `#C6E9EA` / `#002021` | `#2D4C4D` / `#C6E9EA` | leading icon circles, info banners |
| `tertiary` / `onTertiary` | `#196B47` / `#FFFFFF` | `#88D7AC` / `#003B1B` | mint accents: success, business avatars |
| `tertiaryContainer` / `on` | `#B7EFCF` / `#00230B` | `#005331` / `#A4F3C7` | success banners, business avatar |
| `error` / `errorContainer` | `#B3261E` / `#F9DEDC` | `#F2B8B5` / `#8C1D18` | errors, spam warnings |
| `surface` (= background) / `onSurface` | `#F2FBFB` / `#171D1C` | `#0D1514` / `#DCE4E4` | screen background |
| `surfaceContainer` | `#E7F0EF` | `#1B2120` | **groups and cards** |
| `surfaceContainerHighest` | `#DCE4E4` | `#2F3636` | keys, action buttons (off) |
| `onSurfaceVariant` | `#394A49` | `#B7CAC9` | summaries, secondary text |
| `outline` / `outlineVariant` | `#58696A` / `#B7CAC9` | `#829493` / `#394A49` | borders (rare) |

Fixed colours that do not follow the wallpaper:

- `BrandColors.DeepTeal` `#0B3D3A` (icon background) and `BrandColors.Mint` `#69F0AE` (icon accent): decorative only (call backdrop
  glow, ringing ring). Never put text on them without a contrast check.
- `callColors()`: answer green (`#1B6E2E` / dark `#5BC470`) and decline red (`#B3261E` / dark `#F2706B`).
- `statusColors()`: the amber warning container, content and accent (light `#FFE9B8` / `#3A2A00` / `#7A5200`, dark `#4D3A00` /
  `#FFE08A` / `#F2C14E`).

## 3. Type, shape, spacing

Type is the Material 3 scale on the platform font, one step heavier (`Type.kt`). Text is always `sp`; boxes holding text use
`heightIn(min = ...)`.

| Style | Use |
|---|---|
| `displayMedium`/`displaySmall` | the dialed number, the caller name on the call screen, avatar initials of large avatars |
| `headlineMedium` | screen/step titles (a `ScreenScaffold` title does it for you) |
| `titleLarge` | empty-state title, call status |
| `titleMedium` | row titles, card and banner titles, buttons |
| `bodyLarge` / `bodyMedium` | paragraphs / summaries |
| `labelLarge` | section headers, button text, call buttons' labels |
| `labelMedium` | chips, action labels under round buttons |

`TextStyle.tabularFigures()` gives fixed-width digits: use it for timers and counters that tick.

Shapes (`MaterialTheme.shapes`): `extraSmall` 8, `small` 12, `medium` 20 (buttons that are not pills), `large` 24 (cards, groups,
banners), `extraLarge` 28 (keypad keys). Pills use `CircleShape`.

Spacing (`Spacing`): `XSmall` 4, `Small` 8, `Medium` 16 (screen gutter, padding inside cards), `Large` 24 (between sections), `XLarge`
32, `MinTarget` 48, `RowHeight` 56. Use the names, not literals.

## 4. Motion

Springs come from the expressive motion scheme (`MotionScheme.expressive()` in the theme). Animate state changes (mute, hold,
recording, selection) with `motionSpatialSpec()` for movement, size and shape and `motionEffectsSpec()` for colour and alpha. They
return an instant `snap()` when the user turned animations off (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`); the theme provides
that as `LocalReduceMotion`, which `rememberReduceMotion()` keeps up to date. Custom controls get press feedback with
`Modifier.pressScale(interactionSource)`. Anything infinite (the ringing pulse) must check `LocalReduceMotion.current`.

## 5. Components

All take a `modifier` as first optional parameter and are previewed in `Previews.kt` (light and dark).

### `ScreenScaffold(title, onBack?, actions, content)`
A large collapsing top app bar over a `Scaffold`. `content` receives `PaddingValues`: pass it to the scrolling content. The content
must scroll for the bar to collapse. Insets: the scaffold relies on the standard `safeDrawing` insets minus what the parent
consumed; inside the main `NavHost` the status bar, side and bottom-bar insets are already applied and consumed, so nothing is
padded twice.
```kotlin
ScreenScaffold(title = stringResource(R.string.settings_spam_title), onBack = { navController.popBackStack() }) { padding ->
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) { ... }
}
```

### `SettingsGroup(title?) { rows }`
A 24 dp rounded `surfaceContainer` container with the group's rows; an optional `SectionHeader` above it. Put the 16 dp side
margin on the group: `modifier = Modifier.padding(horizontal = Spacing.Medium)`. Space between groups is `Spacing.Medium`; there are
no divider lines.

### `SettingsRow(title, icon?, summary?, onClick?, trailing?, enabled, onClickLabel?)`
Leading icon in a 40 dp tonal circle, title (`titleMedium`), summary (`bodyMedium`, variant colour). With `onClick`: one button of at
least 56 dp with a chevron (override with `trailing`). Without: a read-only, merged label/value row.
```kotlin
SettingsGroup(title = stringResource(R.string.settings_spam)) {
    SettingsRow(
        title = stringResource(R.string.settings_spam_title),
        summary = stringResource(R.string.settings_spam_summary),
        icon = Icons.Filled.Security,
        onClick = { onOpen(SETTINGS_SPAM_ROUTE) }
    )
}
```

### `SwitchRow(title, checked, onCheckedChange, icon?, summary?)`
Whole row toggles (role Switch, TalkBack says On/Off), switch shows a check when on.
### `RadioRow(title, selected, onClick, summary?)`
Whole row selects (role RadioButton). Use for 3 or more options, or options that need a summary.
### `SegmentedChoice(options, selected, onSelected, label)`
2 to 4 short options in a segmented control (>= 48 dp, radio semantics).

### `InfoBanner(kind, title, body?, icon, action?, secondaryAction?, liveRegion)`
Tonal 24 dp banner. `kind` decides colour and default icon; the title is a heading; actions are `UiAction(label, onClick)` text buttons.
```kotlin
InfoBanner(
    kind = BannerKind.Info,
    title = stringResource(R.string.phone_role_banner_title),
    body = stringResource(R.string.phone_role_banner_body),
    action = UiAction(stringResource(R.string.phone_role_banner_action), onRequestPhoneRole)
)
```
**Which kind**: `Info` neutral news or an optional suggestion (set as default phone app, business identified, recording
notice); `Warning` attention without failure (spam suspected, battery optimisation active); `Error` something failed or is
blocked (download failed, recording could not start); `Success` something worked or is on (role granted, backup done). If the text
could be removed without harm, it is `Info`. Use `liveRegion = true` only for banners that appear on their own.

### `StatusChip(text, icon?, kind)`
Small pill for a category, a SIM, "Recording", "Spam". One TalkBack item. Not clickable.

### `SectionHeader(text)`
`labelLarge` in the primary colour, a heading. For sections of plain lists; `SettingsGroup(title)` uses it already.

### `EmptyState(icon, title, body, action?)`
96 dp tonal icon, title, one or two sentences and an optional `UiAction`. Never an empty screen without it.

### `AvatarStyled(name?, size, business, businessIcon, content)`
Initials on a colour picked from the name; a **person icon** for unknown numbers and names without letters (never "#"); a
business variant with a shop (or category) icon. Photo in `content`. Decorative for TalkBack. The old `Avatar(name, ...)` calls it.

### `GroupedItem(index, count, color) { row }`
For lazy lists, where a `SettingsGroup` cannot wrap the items: each item is a `surfaceContainer` card with the side margin,
rounded only at the first and last item of a run (`groupedItemShape`) and separated by a 1 dp gap instead of a divider. Use `color`
for the selected state (`secondaryContainer`), together with a check mark or a state description.
```kotlin
LazyColumn { items(list.size) { i -> GroupedItem(index = i, count = list.size) { SettingsRow(title = list[i].name, onClick = {}) } } }
```

### `SearchPill(value, onValueChange, placeholder, clearLabel)`
Pill-shaped search field (tonal container, search icon, clear button once there is text), at least 56 dp.

### `ChoiceChip(label, selected, onClick)`
Filter chip in the app's style: pill, `primaryContainer` when selected and a check mark in front, so selection is never colour alone.

### `ConfirmDialog(title, body, confirmLabel, dismissLabel, onConfirm, onDismiss, destructive)`
The one confirmation dialog: 28 dp corners, dismiss on the left, confirm on the right, both buttons at least 48 dp. `destructive`
paints the confirm label in the error colour (the verb, such as "Delete", still carries the meaning). Dialogs with fields
(`AlertDialog`) use `shape = MaterialTheme.shapes.extraLarge` and the same button order.

### `PermissionGate(permissions, rationale, buttonLabel, icon, title) { content }`
Until the permissions are granted it shows an `EmptyState` (tonal `icon`, `title`, the rationale and the button).

### Call parts
- `CallButton(kind = Answer | Decline | End, label, onClick, size = 80.dp)`: big round button with its label underneath.
- `CallPillButton(kind, label, onClick, height, showLabel)`: wide pill; icon only for the keypad's green call button, with label
  for the red "End call" bar.
- `CallActionButton(icon, label, onClick, checked?, stateDescription?, enabled, size)`: round tonal action of the call grid;
  `checked` null = plain button, otherwise an on/off control that fills with the primary colour and morphs from a circle to a
  rounded square. `stateDescription` is what TalkBack says ("On", "Off", the audio route).
- `BrandBackground { }`: surface + teal wash + faint mint glow. `PulsingRing(active) { avatar }`: expanding ring around the avatar
  while ringing (nothing with reduced motion).

## 6. Rules for building a screen

1. Wrap the screen in `ScreenScaffold`; side gutter `Spacing.Medium`; scrolling content gets the scaffold's padding.
2. A settings screen is a column (or `LazyColumn`) of `SettingsGroup`s, each with a `title`, separated by `Spacing.Medium`.
   One idea per group. Navigation rows use `SettingsRow` with an icon; booleans use `SwitchRow`; one-of-few uses `SegmentedChoice`
   or `RadioRow`s; read-only values use `SettingsRow` without `onClick` and a `trailing` text or chip.
3. Explanations that matter go in an `InfoBanner` at the top of the screen, never as grey text between rows. Errors are
   `Error` banners with the sentence and a way out.
4. Lists of people or numbers: 56 to 72 dp rows with an `AvatarStyled`, name (`titleMedium`), secondary line (`bodyMedium`), and a
   `StatusChip` for category/spam. No divider lines; use `surfaceContainer` cards for grouped lists.
5. Empty lists show `EmptyState`.
6. Never use colour alone for a state: icon, word or shape too. Every control is at least 48 dp; whole rows are the target.
7. Text comes from string resources (`values` and `values-es`); components take already-resolved `String`s.

## 7. Before and after

| Area | Before (0.1.0) | After |
|---|---|---|
| Colour | wallpaper colours only | deep teal and mint brand scheme; Material You is an option |
| Type / shape | stock M3, 12 to 16 dp cards | one weight-tuned scale, 24 to 28 dp radii, spacing tokens |
| Keypad | grey keys on a flat page, bare number, chips for SIM, plain suggestion rows | pill number display (last digits kept), rounded-square tonal keys with pressed feedback, big green call pill, SIM segmented control, suggestion cards with avatar/business icon and category chip, banner for default-phone offer |
| Incoming call | "#" avatar, plain text buttons | big avatar or business icon with pulsing ring, large name, number and SIM secondary, spam/business banner, large round Answer/Decline with labels |
| Ongoing call | small tonal circles, no clear state | large avatar, tabular timer, 3-column grid of round actions with clear on/off, wide red End pill, backdrop |
| Recording / spam panel | red text row, grey cards | status chip with dot and timer, banners |
| Onboarding | text on a plain page, thin progress bar | header icon per step, stepper, cards in `SettingsGroup`s |
| Navigation | default bar | tonal bar, fade-through between tabs (no animation when reduced) |
| Unknown number avatar | "#" | person icon |
| Settings | flat list of text rows between divider lines, radio rows for the theme, plain text for the diagnostics | status `InfoBanner` (success / info / warning), grouped `SettingsRow`s with tonal icons, theme `SegmentedChoice`, region row, diagnostics as an expandable group |
| Recents | thin app bar, default chips, avatar list with dividers, "#" for unknown numbers | large collapsing bar, pill `ChoiceChip`s, rounded day groups with `AvatarStyled` or the business icon, type icon + word (missed in the error colour too), `StatusChip` for spam and SIM, `EmptyState` |
| Call detail | centred avatar, tonal buttons, grey elevated stats card | header card, filled Call button plus tonal actions, stats as a `SettingsGroup`, calls as grouped rows |
| Contacts | small title, square search box, list with unbounded rows | large bar, `SearchPill`, favourites and letter groups as rounded cards (letter index kept), detail with header card and grouped sections, editor with grouped fields, `EmptyState`s |
| Spam / Data | headings with dividers, grey cards, thin radio buttons | grouped rows, banners for the role and the data status, segmented pickers per level, pack rows with `StatusChip` (installed / update) and trailing actions |
| Sync / Backup / Recording settings | forms and buttons on a plain page | grouped fields and rows, state banners (success / error / warning) instead of coloured text |
| Dialogs | default Material, text buttons in mixed order | `ConfirmDialog` and 28 dp `AlertDialog`s: dismiss left, confirm right, destructive label in the error colour |

## 8. Screens

Which screen uses which pattern (all inside `ScreenScaffold`; the `NavHost` container already applied the insets).

| Screen (package) | Pattern |
|---|---|
| Settings (`feature/settings`) | tab screen: banner, then `SettingsGroup`s of `SettingsRow` / `SwitchRow`; theme `SegmentedChoice`; SIM `RadioRow`s; region dialog; diagnostics group that expands (state description Expanded / Collapsed) |
| Recents (`feature/recents`) | `PermissionGate` > large bar (selection mode changes the title and actions) > `ChoiceChip` row > `GroupedItem` rows per day with sticky `SectionHeader`; `EmptyState` when empty |
| Call detail (`feature/recents`) | header card > action buttons > stats `SettingsGroup` > `GroupedItem` call rows; `ConfirmDialog` |
| Contacts list (`feature/contacts`) | `SearchPill` + group `ChoiceChip`s > `GroupedItem` runs with `SectionHeader`s and the letter index; FAB in `primaryContainer`; overflow menu |
| Contact detail / editor | header card + `SettingsGroup` sections (phones, emails, birthday, notes, account, groups); editor `FormGroup`s of text fields and an error `InfoBanner` |
| Groups, duplicates, vCard import | `GroupedItem` lists, `EmptyState`s, review cards, `InfoBanner` explanations, `SettingsGroup` options |
| Spam home | role `InfoBanner`, `SettingsGroup`s with `SegmentedChoice` per level, `SwitchRow`, link rows |
| Own list / allowed / why flagged | `GroupedItem` rows with a separate delete button, `EmptyState`; why-flagged is a read-only `SettingsGroup` plus banners |
| Data | status banners, one `SettingsGroup` per region with pack rows (chip + trailing text actions + progress), update switches, storage, sources, attribution |
| Sync, Backup, Recording settings, Recordings | grouped fields and rows, state `InfoBanner`s with live regions, `ConfirmDialog`s; recordings as `GroupedItem` cards with the player |

Before and after: the old screens are in `fastlane/metadata/android/en-US/images/phoneScreenshots/`; the new ones have no screenshots
yet (no device was available while redesigning), so they must be checked by eye on a phone in light, dark, large font and TalkBack.
