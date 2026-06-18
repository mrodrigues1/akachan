# Emoji Section Icon Audit

Audit for Linear AKA-189. Scope: production UI/resource emoji in `app/src/main`, cross-checked against Compose tests and design docs where they document intended UI behavior.

## Summary

- Primary section markers currently use emoji in home cards, feature selection rows, history empty states/cards, partner dashboard cards, widgets, and a few notification titles.
- App navigation does not use emoji section markers. Top app bars and actions use Material icons; home cards and partner cards are the main navigational section entry points that still render emoji.
- Repeated cards share `HistoryCard`, whose emoji badge is rendered in a 44dp square badge with 20sp text.
- Accessibility is mostly handled by parent `contentDescription` or merged semantics. Several decorative emoji call `clearAndSetSemantics {}`; `HistoryCard` badges merge into row semantics unless the row supplies a richer parent description.
- Two sections have inconsistent markers: growth uses `📈` in home/feature selection but `📏` in the growth empty state, and partner sleep uses `💤` while core sleep uses `🌙`.

## Section Inventory

| Section | Current marker(s) | Production locations | Size/state notes |
|---|---|---|---|
| Breastfeeding | `🤱`, sometimes `🍼` | Feature picker row (`FeaturePicker.kt`); breastfeeding side chips (`breastfeeding_side_chip`); unified feeding history breastfeeding rows use `🤱`; breastfeeding screen/history cards still use `🍼`; notification titles and collapsed active notification prefix use `🍼`. | Feature picker domain rows use `titleMedium`; feature rows use `titleMedium`. History badges use 44dp container + 20sp emoji. Home breastfeeding card uses `🍼` at `headlineMedium`, active state changes card color/elevation and shows active badge. Notification prefix is plain `RemoteViews` text. |
| Bottle feed | `🍼` | Home bottle-feed quick-action card; unified feeding history bottle rows; notification titles for feeding active/paused/switch sides; widget feed preview and standard widget feed block. | Home card uses `headlineMedium` and clears emoji semantics. History badge uses 44dp/20sp. Widget standard feed block has responsive emoji text presets from 14sp to 22sp. |
| Feeding history | `📋` | Home feeding history tile. | Home card uses `headlineMedium`, minimum 96dp card height, decorative semantics cleared; summary text varies by current feed count/volume. |
| Pumping | `🥛` | Feature picker row; home pumping card; pumping history empty state and session cards. | Home card uses `headlineMedium`, active pumping state changes color/elevation and shows pause/play active badge. Pumping history empty state uses `displaySmall`; cards use `HistoryCard` 44dp/20sp. |
| Milk inventory/stash | `🧊` | Feature picker row; home inventory card; inventory empty state; partner inventory card; milk stash Glance widget; widget preview resources. | Home card uses `headlineMedium`; inventory empty state uses `displaySmall`; partner card uses a 40dp square badge with `titleSmall`; milk stash widget off-state uses 22sp and small widget content omits emoji when data is present. |
| Sleep | `🌙`, `😴`, partner `💤` | Feature picker row/domain; home sleep card; sleep current/history empty states; sleep quick-start button labels; sleep type domain model; schedule entries default nap emoji; notification title for active sleep; standard widget sleep block; partner active/history sleep cards use `💤`. | Home card uses `headlineMedium`, active state changes card color/elevation and shows active badge. Empty states use `headlineLarge`. Quick-start labels embed emoji in button text. Widget sleep block has 14sp-22sp presets. Partner active sleep uses 52dp colored badge + `headlineSmall`; partner history uses `HistoryCard`. |
| Wake time | `🌅` | Sleep wake-time set/woke string resources rendered on the sleep screen. | Not a section marker; appears inline in a row/button label with separate Material add/edit icon. Replacement can likely follow sleep/wake semantic icon treatment. |
| Diapers | `🧷`, type markers `💧`/`💩`/`🌀` | Feature picker row/domain; home diaper card; diaper history empty state; partner diaper card title; diaper type segmented control and history rows use type emojis from `DiaperType`. | Home marker uses `headlineMedium`. Empty state uses `headlineLarge`. Partner card embeds emoji in title text. Diaper type controls are stateful segmented buttons; selected type must remain legible while disabled mid-save. History rows use `titleLarge` type emoji. |
| Growth | `📈`, empty state `📏` | Feature picker row/domain and home growth card use `📈`; growth empty state uses `📏`. | Home marker uses `headlineMedium` in a minimum 120dp card; empty state uses `displaySmall`. Replacement should decide whether growth needs one consistent section icon or type-specific measurement icon. |
| Trends | `📊` | Home trends card. | Home marker uses `headlineMedium` in a minimum 120dp card. No feature-picker entry because trends are surfaced as a home tile rather than a feature toggle. |
| Milestones | `🎉` | Feature picker row; home milestones card; milestones empty state. | Home marker uses `headlineMedium`; empty state uses `displayMedium` and milestone accent color. |
| Tips | `✨` | Home tip card. | Not a section marker, but it is a repeated contextual icon. Uses `titleMedium` in a compact bordered row. |
| Sleep/event cues | `😪`, `😋`, `😣`, `🤒`, `🦷`, `✈️` | `CueQuickTapRow` on home/sleep surfaces; tests assert all six chips. | Not section markers. Rendered inline in `FilterChip` label text with `labelMedium`; selected state temporarily shows a 16dp check icon and animated chip scale. |
| Warnings/errors | `⚠`, `⏱` | Manual breastfeeding/sleep error strings use `⚠`; feeding-limit notification title uses `⏱`. | Not section markers. Warning tokens are extended theme values and should stay separate from section icon replacement. |
| Greeting | `👋` | Home greeting string. | Not a section marker; inline text only. |

## Location Inventory

### Navigation

- No emoji section markers are used in `AppNavGraph` or top app bar navigation.
- Section entry navigation is mostly through home cards: breastfeeding, sleep, pumping, inventory, bottle feed, diapers, growth, trends, milestones, and feeding history.
- Top bars use Material icons for back, settings, history, add, refresh, and overflow actions.

### Home Cards

- `HomeTileContent.kt`: breastfeeding `🍼`, sleep `🌙`, tip `✨`, partner sharing uses a Material People icon rather than emoji.
- `HomeScreen.kt`: pumping `🥛`, inventory `🧊`, bottle feed `🍼`, diapers `🧷`, growth `📈`, trends `📊`, milestones `🎉`, feeding history `📋`.
- Home card emoji are decorative and typically call `clearAndSetSemantics {}` while the card supplies a section-level `contentDescription`.
- Active breastfeeding/sleep/pumping states do not swap emoji; they animate container color/elevation and show an active status badge.

### Feature Selection / Settings

- `FeaturePicker.kt` uses emoji for both feature domains and individual features:
  `FEEDING=🍼`, `SLEEP=🌙`, `DIAPERS=🧷`, `GROWTH_DEVELOPMENT=📈`;
  `BREASTFEEDING=🤱`, `BOTTLE_FEED=🍼`, `PUMPING=🥛`, `INVENTORY=🧊`, `SLEEP=🌙`, `DIAPERS=🧷`, `GROWTH=📈`, `MILESTONES=🎉`.
- The picker is reused by onboarding/feature management flows, so replacement icons need enabled/disabled/on/off switch states and expanded/collapsed domain rows.
- General settings rows use Material icons in `DataSection`, `WarningSurface`, feed/sleep settings, and inventory settings; no settings section header emoji were found.

### Empty States And Headers

- Breastfeeding history empty state: `🍼`, `displaySmall`.
- Unified feeding history empty state: `🍼`, `displaySmall`.
- Sleep today/history empty states: `🌙`, `headlineLarge`.
- Diaper history empty state: `🧷`, `headlineLarge`.
- Growth empty state: `📏`, `displaySmall`, inconsistent with home/feature `📈`.
- Pumping history empty state: `🥛`, `displaySmall`.
- Inventory empty state: `🧊`, `displaySmall`.
- Milestones empty state: `🎉`, `displayMedium`.
- Sticky day headers are text-only uppercase labels and do not use emoji.

### Cards And Repeated Components

- `HistoryCard` requires a `badgeEmoji` string and renders it in a 44dp badge at 20sp. It is used by breastfeeding, unified feeding, pumping, partner feed, partner sleep, and other history-style rows.
- Diaper history uses a custom row instead of `HistoryCard`; it renders `DiaperType.emoji` as `titleLarge`.
- Partner inventory uses a custom 40dp badge with `🧊` at `titleSmall`.
- Partner active sleep uses a custom 52dp badge with `💤` at `headlineSmall`.
- Partner diaper card embeds `🧷` directly into the title string, not a separate badge.

### Widgets

- Standard Glance widget uses `FEED_EMOJI=🍼` and `SLEEP_EMOJI=🌙`; text size presets range from 14sp to 22sp depending widget size.
- Milk stash widget uses `MILK_EMOJI=🧊`; off-state shows it at 22sp. Populated small content uses numeric volume and bag count instead of the emoji marker.
- XML widget preview uses localized string resources `widget_preview_feed_emoji=🍼` and `widget_preview_sleep_emoji=🌙`.

### Notifications

- String resource notification titles embed emoji for feeding/sleep active states: `🍼 Time to switch sides`, `🍼 Feeding paused`, `🍼 Feeding active`, `🌙 Sleep active`.
- `NotificationViewBuilders.buildFeedingActiveCollapsedView` sets a dedicated collapsed title prefix `"🍼 "`.
- Feeding limit uses `⏱` and warning/error strings use `⚠`; these are not section markers but should be reviewed during icon-system replacement.
- Predictive feed/sleep, nap reminder, stash expiration, and partner stash titles do not embed section emoji in current resources.

## Source Checklist

- `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt`
- `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- `app/src/main/java/com/babytracker/ui/features/FeaturePicker.kt`
- `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt`
- `app/src/main/java/com/babytracker/ui/component/CueQuickTapRow.kt`
- `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt`
- `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt`
- `app/src/main/java/com/babytracker/ui/feeding/UnifiedFeedingHistoryScreen.kt`
- `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt`
- `app/src/main/java/com/babytracker/ui/sleep/SleepHistoryScreen.kt`
- `app/src/main/java/com/babytracker/ui/diaper/DiaperSheet.kt`
- `app/src/main/java/com/babytracker/ui/diaper/DiaperHistoryScreen.kt`
- `app/src/main/java/com/babytracker/ui/growth/GrowthScreen.kt`
- `app/src/main/java/com/babytracker/ui/pumping/PumpingHistoryScreen.kt`
- `app/src/main/java/com/babytracker/ui/inventory/InventoryScreen.kt`
- `app/src/main/java/com/babytracker/ui/milestone/MilestonesScreen.kt`
- `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt`
- `app/src/main/java/com/babytracker/ui/partner/PartnerDiaperCard.kt`
- `app/src/main/java/com/babytracker/widget/WidgetContent.kt`
- `app/src/main/java/com/babytracker/widget/MilkStashWidgetContent.kt`
- `app/src/main/java/com/babytracker/util/NotificationViewBuilders.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-pt-rBR/strings.xml`
- `app/src/main/res/layout/widget_preview.xml`
- `app/src/main/java/com/babytracker/domain/model/SleepType.kt`
- `app/src/main/java/com/babytracker/domain/model/SleepSchedule.kt`
- `app/src/main/java/com/babytracker/domain/model/DiaperType.kt`

## Replacement Requirements For Follow-Up Work

- Provide icon treatments for at least these semantic sections: breastfeeding, bottle feed, feeding history, pumping, inventory/stash, sleep, nap, diapers, diaper wet/dirty/both types, growth, trends, milestones, tips, partner sleep, and cue types.
- Support at minimum these sizes: inline chip text (`labelMedium` equivalent), feature row (`titleMedium`/`titleLarge`), home card (`headlineMedium`), empty state (`headlineLarge`/`displaySmall`/`displayMedium`), history badge (44dp/20sp), partner compact badge (40dp), partner active badge (52dp), widget text presets (14sp-22sp), and notification prefix/title usage.
- Preserve state distinctions currently expressed outside emoji: selected feature switches, active session color/elevation and status badge, disabled-but-selected diaper segmented button, cue chip selected check animation, dark-mode `HistoryCard` border, and widget size classes.
- Decide whether `🍼` should remain a broad feeding marker or split consistently into breastfeeding `🤱`, bottle feed `🍼`, and feeding history `📋`.
- Decide whether growth should standardize on `📈` or use measurement-specific iconography where the current empty state uses `📏`.
- Decide whether partner sleep should align with core sleep `🌙` or keep a distinct active/asleep `💤` marker.
