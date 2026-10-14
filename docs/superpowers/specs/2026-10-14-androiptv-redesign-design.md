# AndroIPTV interface redesign — design specification

Date: 2026-10-14  
Project: picajoso/androiptv  
Status: approved design; awaiting user review of this specification

## Goal and constraints

Redesign the existing Android app for phones and tablets in portrait and landscape. Preserve its Kotlin and programmatic Android Views architecture and Media3 playback. Do not migrate to Compose, add third-party dependencies, or add Android TV support. Treat the supplied HTML prototype and handoff README as the source of truth for visual tokens, measurements, copy, and interactions.

Use the Modernist tokens from the handoff: square corners, left-aligned text and button labels, minimum 44 dp touch targets, divider rules, Archivo typography, and Lucide vector icons. Package Archivo locally under app/src/main/res/font/ with its OFL license. Add only Lucide vectors used by implemented screens.

## Architecture

Keep MainActivity as the app and playback state coordinator, but move rendering into focused Kotlin files under es.maciptv.android.ui: theme and dimensions, reusable channel row, portrait screens, landscape panels, settings, provider sheet, and dialogs/toasts. Add a ChannelName presentation helper separately. Continue building Views from Kotlin; do not add XML layouts or Compose.

Keep one PlaybackController and one PlayerView. The PlayerView may move between containers when screens change. Navigation between preview, portrait player, landscape panels, and fullscreen must not call play(), change the selected video, or reload the media item. Handle configuration changes in the existing activity lifecycle path and preserve the current fullscreen behavior in Phase 1.

Use the current ListView/adapter virtualization for large playlists, replacing its row presentation with reusable channel rows. Keep current networking and parser implementations unless a focused change is required to support the new screens. On orientation changes preserve the selected tab, group, search text, and channel-list scroll position.

## State and source migration

Extend SecureStore to store multiple saved sources plus an active source identifier. Encrypt the source collection with the existing Android Keystore AES/GCM approach. On first load, migrate the legacy single encrypted SourceConfig as a saved source named “Mi lista”; associate the legacy last-channel ID with that source. Preserve favorites and recents as their existing values. Do not change stableId() or its hash inputs. New selections update the selected source’s last-channel ID while retaining the migrated last channel. Delete the old encrypted source only after the new collection has been written and read back successfully. Removing one source must not clear the last-channel ID belonging to any other source.

Support adding, selecting, refreshing, and deleting saved sources. Keep M3U URL, M3U file, Xtream Codes, demo, XMLTV, HTTP authorization, and local-server authorization behavior. File imports continue using the Android document picker. The provider bottom sheet validates required fields and saves through the existing secure store.

Persist the resume-last-channel preference and fill/fit preference. Implement sleep timer choices of No, 30, 60, and 90 minutes; changing to No cancels the pending timer. When the timer expires, pause playback. Do not persist the active timer; cancel it in onDestroy() and when removing the active source. Do not expose source credentials in logs, errors, or documentation.

## Channel names and logos

Add ChannelName as a display-only transformation. Recognize country prefixes (|XX|, XX:, XX - ), quality/codec suffixes (UHD, 4K, FHD, HD, SD, HEVC, H265), and the acronym/brand exceptions in the handoff. Fall back to the original name if parsing produces no useful display name. Compute cleaned names when a playlist is loaded, not while drawing each row. Filtering matches both Channel.name (original) and the cleaned display name, as well as group names.

Load Channel.logo images off the main thread using the existing OkHttp dependency through the existing SafeNetwork validation policy. Reuse the single SafeNetwork/OkHttpClient instance for the active source across playback and logo requests. Follow each source’s HTTP authorization: if a logo URL uses HTTP and HTTP is not authorized for that source, do not request it, show channel initials, and indicate the restriction on that source’s settings row. Bound response bytes and decode images to the on-screen size rather than source resolution. Bound the in-memory LruCache to one sixteenth of the available app heap, measured in bitmap bytes. Show initials when a logo is absent or fails. Do not add Coil or another image library.

## Phase 1 — screens, settings, and existing fullscreen

Deliver all of the following as one compilable phase:

### Portrait navigation and Channels

- Three bottom tabs: Canales, Guía, Ajustes. Use the handoff’s 56 dp height, 2 dp top rule, equal widths, left alignment, icons, and active state. Until the Settings screen is delivered in Task 6, Ajustes opens the current source dialog so intermediate builds remain usable.
- Channels screen with a 16:9 live preview when a channel is selected, current programme and progress, source/title header, search, horizontally scrolling group chips, virtualized rows, favorites, now/next information, progress, and specified empty/loading states. Refresh programme/progress data once per minute on a background executor or for visible rows only; never block the main thread.
- Selecting a channel from a row starts it and opens the portrait player. Preview taps open the player; its explicit play/pause and fullscreen controls remain operable. The current programme and its progress are cached as derived view data and refreshed once per minute; row binding reads that cached value instead of recalculating programme state per row.
- Search includes original and cleaned channel names and group names. Group order is Todos, Favoritos, Recientes, then playlist group order.

### Portrait player and guide

- Portrait player with 16:9 video, custom back/fullscreen controls, current channel/programme metadata, favorite control, previous/next, external playback, and today’s programme table. Use custom portrait controls and set PlayerView.useController = false outside fullscreen; the provisional fullscreen screen may use the Media3 controller.
- Guide tab with channel selector, today/tomorrow selector, channel metadata, “Ver canal”, and programme list. The “Mañana” view filters programme entries to the next local calendar day. Existing guide data and parsing remain the source of programme content. Reminder controls are deferred to Phase 3.

### Settings and provider management

- New Ajustes screen matching the supplied design, including saved-source list, refresh/remove actions, four source-add choices, playback image mode, sleep timer, and resume-last-channel switch.
- Multiple-source migration and management are part of Phase 1, despite appearing later in the original phase outline.
- Provider configuration uses the specified custom bottom sheet for URL M3U and Xtream fields and existing file-picker flow for M3U files. Demo remains one-tap. Preserve HTTP/local-server confirmations and URL/username validation.
- Use the specified custom confirmation dialogs for deletion and opening a credential-bearing stream externally, plus a short custom in-app notice for feedback/errors. Avoid placing sensitive URLs in notices.

### Landscape panels

- On landscape or wide layouts, show the 44 dp accent header and three columns: groups, channels, player. Use handoff column sizes as minimum/reference widths (170 dp, 250 dp, remaining width), allowing sensible bounds on tablets.
- Group selection, search, favorites, previous/next, source refresh/settings, fit mode, sleep timer, external open, and playback controls work without switching to a different channel screen.

### Fullscreen in Phase 1

Preserve the existing fullscreen entry/exit, landscape orientation request, hidden system bars, and fill/fit behavior. Keep the player accessible and operational. The advanced translucent control overlay, brightness/volume sliders, lock, auto-hide, and channel drawer are Phase 2. Set PlayerView.useController = true only for this provisional fullscreen view; portrait playback uses custom controls and no Media3 controller. Every icon-only button has a Spanish accessibility description (contentDescription).

## Phase 2 — advanced fullscreen controls

Replace the basic fullscreen toolbar with the dark overlay described in the handoff: auto-hide after four seconds while playing, top channel/programme header, brightness and volume sliders, previous/next names, live progress, six action cells, lock/unlock affordance, and a 300 dp right-side channel drawer. Selecting a channel in the drawer changes playback without exiting fullscreen. Preserve system back/gesture behavior and the player instance. Restore the system’s original screen brightness when exiting fullscreen, including when the activity is destroyed while fullscreen is active.

## Phase 3 — reminders and Cast

Add programme reminders using AlarmManager and notifications, and Cast using Media3 Cast only after reviewing its dependency and permission implications. Replace the “Buscando dispositivos…” placeholder with real device discovery. Expand logo caching only if measurement shows the in-memory cache is insufficient. No dependency is added without the user’s approval.

## Acceptance criteria

1. The project builds after Phase 1 and remains a Views-based Kotlin app with no new library dependency.
2. Existing M3U URL/file, Xtream, demo, XMLTV, HTTP/local authorization, favorites, recents, previous channel, resume behavior, external playback warning, and fill/fit behavior remain available.
3. A legacy installation opens with its source, favorites, recents, and last channel preserved; stableId() output is unchanged.
4. Display-name cleanup never mutates the stored/original channel name, and searches match both forms.
5. Switching between preview, player, landscape panels, and fullscreen may reparent PlayerView but never calls play(), changes the selected video, or reloads its media item.
6. The existing fullscreen controls continue to work in Phase 1; Phase 2 adds the enhanced overlay.
7. Logos load asynchronously through existing networking code, respect URL validation and bounded decoding, and fall back to initials.
8. Screen layouts follow the supplied tokens and avoid rounded corners, unintended shadows, centered button labels, and undersized touch targets.
9. After Phase 1 is built and reviewed, stop and wait for the user before starting Phase 2. Repeat the same review gate after Phase 2 before Phase 3.
10. Source migration deletes legacy encrypted data only after writing and verifying its replacement; removing a source preserves other sources’ last-channel values.
11. Channel names are precomputed when loading data; current programme and progress refresh once per minute, not while binding each row.
12. Rotation preserves tab, group, search, and list scroll position. “Mañana” displays only programmes in the next local calendar day.
13. HTTP-blocked logo URLs fall back to initials and expose the restriction in settings. Icon-only controls include Spanish accessibility descriptions.
14. The in-memory sleep timer is not stored and is cancelled on destruction or removal of the active source. Phase 2 restores the original system brightness when fullscreen ends.
15. Until Task 6, Ajustes opens the existing source dialog. One SafeNetwork/OkHttpClient instance is reused per active source; logo bitmaps decode to screen dimensions and their memory cache uses one sixteenth of the app heap.
16. Programme/progress refresh runs in the background or updates visible rows only. Dates and times use the device locale. Phase 1 uses custom portrait controls (PlayerView.useController=false) and the Media3 controller only in provisional fullscreen.
