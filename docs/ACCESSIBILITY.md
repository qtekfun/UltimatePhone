# Accessibility and translations audit

Phase 6 audit of the UI against the non-functional requirements in `docs/spec/03-funcionalidades.md`: TalkBack works, large font sizes work, contrast is good, and the app is fully available in English and Spanish.

The audit was done by reading the code (no device or emulator was available). Section 4 lists what still needs a manual pass on a real device.

## 1. Conventions used in the UI

- A list row is one TalkBack item: `clickable`/`combinedClickable`/`toggleable`/`selectable` (these merge their children) or `semantics(mergeDescendants = true)` for rows that are not clickable. Buttons nested in a row (the "details" button of a call, the delete button of a list entry) stay separate focus stops, which is intended.
- Custom clickable rows set a `Role` (`Button`, `Switch`, `Checkbox`, `RadioButton`) and an `onClickLabel` when the action is not obvious.
- Rows with a switch, checkbox or radio button are clickable as a whole (`toggleable`/`selectable`, with the control's own `onCheckedChange = null`), at least 48 dp high.
- Decorative icons and avatars have `contentDescription = null`; the shared `Avatar` hides its initials from TalkBack because the name is always next to it.
- State is never conveyed by colour alone: missed calls (icon plus the word "Missed"), spam (icon plus "Spam" and the reason), recording (dot plus the word "Recording" plus the time), errors (always a sentence), selected rows (check mark plus the `selected` semantics), favourites (star plus a "Favorite" state description), new calls (bold plus a "New" state description).
- Headings (`heading()`) are set for screen titles, section titles and card titles.
- Changes that appear without the user touching anything use a polite live region: call state (ringing, calling, ended), recording started, recording errors, spam warning, sync/backup/import progress and results, mic test results, selection counts.
- Fixed pixel sizes are only used for touch targets and icons. Text is `sp`, boxes holding text use `heightIn(min =)`, and screens that can overflow scroll.
- Controls whose label alone is ambiguous when read one by one name their target ("Install: Businesses (Spain)", "Delete: <recording>", "Remove: <source>").

## 2. What was verified by reading the code

| Screen | Checked | Fixed in this phase |
|---|---|---|
| Keypad | Key buttons, call button, delete, SIM chips, suggestions | Keys read as the digit only (not "2 A B C"), star/pound spoken as words, long press on 0 has a label; delete is one control with "delete digit" and "clear number" actions; number field has a long-press "paste" label; suggestion rows are buttons (48 dp, "Call" action, two-line names); SIM chips wrap instead of overflowing; the number may wrap at large font; banner title is a heading |
| In call | Controls, state, touch targets, large text | Call state text is a polite live region (not the running timer); the keypad keys (touch-only before) are real buttons with names and a click action; mute/hold/keypad/merge announce "On/Off"; add call, audio and record are plain buttons (audio announces its current route); name up to three lines; control labels up to three lines; the recording bar has the word "Recording" as a live region plus a dot and the time; recording notices are live regions |
| Recents | Row, filters, selection, menu | Row is a button with "new" and "selected" state, check mark is decorative (no double reading), day headers are headings, selection count is plural and a live region |
| Call detail | Header, actions, stats, entries | Name is a heading, each statistic and each call entry is one item |
| Contacts list | Search, groups, rows, index | Section letters are headings; favourite state is spoken; rows allow two lines; the A-Z strip is hidden from TalkBack (the list scrolls normally), is 48 dp wide and ignores the font scale so 26 letters still fit; search field has a spoken label; selection count is plural, a heading and a live region |
| Contact detail | Top bar, phone/email rows, groups | Phone and email rows are one button each with a "Call <number>" or "Send an email to <address>" action (the duplicate trailing button is skipped); name and sections are headings |
| Contact edit | Fields, type chips | At font scale 1.3 and above the type chip moves under the field so the field is not squeezed; save error is a live region |
| Duplicates, groups, import | Lists, dialogs, results | Count is a heading; "keep two" message is a live region; group name dialog scrolls; import screens scroll, progress and result are live regions |
| Settings, Spam, Spam lists, Why flagged | Rows, switches, segmented buttons | Navigation rows have a role and 56 dp height; the "business is not spam" switch is clickable as a whole row; segmented buttons are at least 48 dp; list entries are one item; label/value rows are merged |
| Data | Packs, sources, switches | Pack and source buttons name their target; switches are whole-row; buttons wrap (and move under the text at large font); source test result is a live region |
| Onboarding | Steps, progress, cards | "Step x of y" is a live region and the bar is hidden (it says the same); card titles are headings; region rows are whole-row checkboxes; decorative bullets are not read |
| Sync, Backup | Forms, results | Progress/results are live regions; section titles are headings; switch row is whole-row; buttons wrap |
| Call recording settings, Recordings list | Rows, player | Already had whole-row switches/radios and live regions; list row got a role; each recording button names its recording; title and subtitle merge into one item |

Dialogs (`AlertDialog`) announce their title when they open. The system file pickers and permission prompts are outside the app.

## 3. Strings and translations

- Every user-visible string is a resource; a search for string literals in `Text(...)`, `contentDescription = "..."` and `Toast` found none. Decorative separators (" · ", "•") are the only literals left.
- `StringResourcesTest` (JVM, reads the XML from the source tree) fails when: a name exists in only one language, a `strings_*.xml` file has no Spanish counterpart, the resource kind differs, format placeholders differ, a plural lacks `one`/`other` or has different placeholders, an array has a different size, a string is identical in both languages without being in the allow-list (brand names, technical terms, pure formats), the allow-list has stale entries, a string is blank or uses `...` instead of the ellipsis, or the Spanish uses the formal "usted". It also checks that the app declares only English and Spanish (`generateLocaleConfig = true`, `unqualifiedResLocale=en`).
- The generated `locales_config.xml` lists `en` (default) and `es`. The app name `UltimatePhone` is the same in both languages; the Android notification strings of the telecom module are translated.
- Numbers that can be 1 use `plurals` (selected counts, exported/found/skipped contacts, group and duplicate members, source entries, unreadable lines). Strings with several counters were reworded as "Label: n" so no plural form is needed.
- Spanish wording made consistent: tú form; "app de teléfono"; "lista de spam"; "números permitidos" (not "lista blanca"); "comercios" (not "negocios"); "paquetes de datos"; "archivo" (not "fichero"); "app" (not "aplicación") except where Android or Nextcloud use that name; "En espera" for a held call and "Llamada en espera" for a waiting one; "Eliminar" for deleting an item, "Borrar" for clearing text or the history.
- The setup guide's Nextcloud text no longer says the feature "arrives in a later update" (it exists, in Settings).

## 4. Needs a manual TalkBack pass on a device

Test script (about 15 minutes, TalkBack on, then again with the font size at the largest setting and the display size at the largest):

1. Keypad: swipe through the keys; each is announced as a digit (or "star", "pound") and "button". Double tap "0" and long press it (action "Enter plus sign"). Type a number, go to the delete button: it should offer "Delete digit" and "Clear number".
2. Place a call to a second phone: on answer the state is announced; during the call check "Mute" says On/Off, "Keypad" opens the tones keypad, each key can be double-tapped (the other phone hears the tone), "Audio" says the current route, "Add call" is a plain button.
3. Receive a call with a number from the spam list: the spam warning is read when the screen appears; "Answer" and "Decline" are reachable first or second.
4. Record a call: "Recording" is announced once, the timer is not read every second, "Stop recording" is reachable.
5. Recents: a row reads name, type, count, SIM, spam state and time as one item, then a separate "Details" button. Long press selects; the count is announced.
6. Contacts: the list scrolls with swipes; section letters are headings (use heading navigation); the A-Z strip is not a focus stop. Open a contact: the phone row reads as one "Call" button.
7. Edit a contact at the largest font: the phone field is wide enough to type and the type chip is under it.
8. Settings and all sub-screens: switches are one item per row and say On/Off; segmented spam buttons say selected/not selected.
9. Data: buttons say what they act on. Start a download: progress/result is read.
10. Run the setup guide again from Settings: the step number is announced on each step.
11. Repeat step 2 and 5 in landscape and with the largest font: nothing is clipped, hang-up button is always visible, screens scroll.
12. Set the phone language to Spanish and to English and scan every screen once; check that no English text appears in Spanish mode.

## 5. Known limitations

- The dialer keypad and the in-call keys are touch-first; TalkBack users enter digits by double tap on each key (no typing field with a system keyboard).
- The A-Z fast scroller is not available to TalkBack users (the list itself is fully scrollable and has headings).
- Colours are the brand teal and mint scheme by default (every text pair is at least 4.5:1, checked by `BrandColorsTest`, see `docs/DESIGN.md`). With "Use system colours" on, they come from the dynamic Material You scheme chosen by the wallpaper; contrast of text on those surfaces is guaranteed by Material, but a very low-contrast wallpaper could in theory reduce it. The call green and red come from `:core:designsystem` and keep at least 4.5:1 (checked by its unit test) in light and dark.
- Animations (key press, mute/hold/record state changes, the ringing pulse, tab transitions) are skipped when the system animation scale is 0.
- A few labels that Android itself defines (names of OEM settings pages in the battery guide) are quoted as the manufacturer writes them and can differ by model and language.
- Section titles for the system pickers (file chooser, contact picker) and the permission dialogs follow the system language and TalkBack settings, not the app.
- Not verified without a device: real TalkBack speech output and focus order on each OEM, behaviour at display size "largest" on small phones, and the font scale above 2.0.
