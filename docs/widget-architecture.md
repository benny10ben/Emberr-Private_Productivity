# Widget Architecture

Android home screen widgets, built with Glance. Android only — desktop has none.

## The widgets

| Widget | Shows | Space |
|---|---|---|
| `note` | one chosen note's content | note is picked, space irrelevant |
| `noteshortcut` | one chosen note as a button | same |
| `notelist` | recent notes | pinned or active |
| `tasks` | open tasks | pinned or active |
| `todaytasks` | today's tasks | pinned or active |
| `calendar` | a month grid | pinned or active |
| `calendaragenda` | a month grid plus a day's events | pinned or active |
| `upcomingevents` | events grouped by date | pinned or active |

The last six are the "aggregate" widgets. They show a whole space's data, so they can be pinned to a space.

## Files per widget

Each widget is its own package under `presentation/widget/` with the same file set.

| File | Job |
|---|---|
| `XWidget.kt` | the Glance `GlanceAppWidget`, draws the UI, builds tap intents |
| `XWidgetReceiver.kt` | manifest entry point, hands the widget to Android |
| `XWidgetContentReader.kt` | reads the database, returns a drawable content object |
| `XWidgetContent.kt` | the serializable content it returns |
| `XWidgetRefresher.kt` | reads/writes the cache and pushes a redraw |
| `XWidgetCoordinator.kt` | watches the database while the app runs |
| `XWidgetPickerActivity.kt` | the setup screen shown when the widget is added |
| `XWidgetConstants.kt` | preference keys and sizes |
| `XWidgetActionReceiver.kt` | handles taps the widget handles itself (only 4 widgets) |

Shared across all of them: `WidgetSpaceState.kt`, `WidgetIntentExtras.kt`, `WidgetNoteChooser.kt`, `WidgetSpaceChooser.kt`, `WidgetNoteSource.kt`, `WidgetPalette.kt`, `WidgetLog.kt`, `TaskCompletionToggle.kt`.

## How data reaches the screen

```
Room  →  ContentReader  →  Content object  →  cache (Glance prefs)  →  drawn
```

Two things trigger that:

**The coordinator**, while the app is running. It collects a database Flow, debounces (~150ms), and pushes to every instance. Started once per widget in `Application.kt`.

**Android itself**, when the app is not running. It calls `provideGlance`, which loads fresh content and falls back to the cache if the database is unreachable.

## Two render paths

Both exist and both must be kept working.

- `provideGlance` → `provideContent { XWidgetBody(...) }` — used by Glance.
- `pushRenderedX` in the refresher → `GlanceRemoteViews().compose { XWidgetBody(...) }` — used by the coordinator for an immediate redraw.

They call the same `XWidgetBody`. Anything the body needs has to be passed at both call sites.

## Per-instance state

Every instance has its own Glance preferences, so two copies of the same widget can differ. Stored per instance:

- the cached content
- the pinned space (`selected_space_id`)
- the chosen note (`note`, `noteshortcut`)
- widget-specific state such as the shown month or whether completed tasks are visible

## Spaces

`readWidgetSpaceId(context, glanceId)` answers "which space is this instance showing": the pinned space if set and still live, otherwise the active space.

The pin is written by the picker activity and cleared when its space is deleted (`DeletedSpaceTrigger` → `clearWidgetPinsForSpace`). A pin pointing at a deleted space falls back to the active space rather than rendering empty.

Coordinators watch **all** spaces (`...AcrossSpacesFlow`) and each instance then re-reads its own space. A change in any space wakes the widgets; each one still draws only its own.

## Taps

Widgets can't navigate. They start `MainActivity` with an extra, and `MainActivity.consumeWidgetRoute` turns it into a route.

| Extra | Opens |
|---|---|
| `widget_note_id` | that note |
| `widget_daily_screen` + date | the journal |
| `widget_tasks_screen` | Tasks |
| `widget_calendar_screen` | Calendar |
| `widget_home_screen` | Home |
| `emberr-calendar-day://` / `emberr-calendar-event://` | Calendar, scrolled to a date or event |

Opening a **note** never changes the active space — note ids are unique. Every other destination is a space-scoped screen, so those intents carry `widget_space_id` and `MainActivity` switches to that space first.

Taps the widget handles without opening the app (ticking a task, changing month) go to `XWidgetActionReceiver` as a broadcast instead.

## Adding a widget

1. The nine files above.
2. `res/xml/x_widget_info.xml`, with `android:configure` if it needs a picker.
3. A `<receiver>` and, if it has a picker, an `<activity>` with `APPWIDGET_CONFIGURE` in `AndroidManifest.xml`.
4. Koin registrations for its content reader and coordinator in `AndroidModule.kt`.
5. `coordinator.start()` in `Application.kt`.

## Rules to follow

- Pass anything new to `XWidgetBody` from **both** render paths, or half the redraws will be wrong.
- `readWidgetSpaceId` is a suspend function. Call it before `compose { }`, never inside it.
- Never read the active space directly in a widget. Use `readWidgetSpaceId`, or pinning is ignored.
- Content objects are cached as JSON. Changing their shape invalidates old caches; decoding already falls back to null, so that is safe but does blank a widget until its next refresh.
- Coordinators only run while the app process is alive. Anything that must survive the app being killed belongs in `provideGlance`.
