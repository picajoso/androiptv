# AndroIPTV Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved AndroIPTV redesign Phase 1 for phones and tablets while preserving existing provider, playback, and security behavior.

**Architecture:** Keep Kotlin programmatic Views, one MainActivity coordinator, one PlaybackController, and one PlayerView. The PlayerView may move between containers; screen transitions preserve the selected video and never call play() or reload the media item. Extract rendering into focused UI classes; make derived channel data, source migration, and image loading explicit components.

**Tech Stack:** Kotlin, Android SDK 37 / minSdk 26 / targetSdk 35, Android Views, Media3, existing OkHttp, Android Keystore AES/GCM. No new library dependencies.

**Spec:** [2026-10-14-androiptv-redesign-design.md](../specs/2026-10-14-androiptv-redesign-design.md)

## Global Constraints

- Use Kotlin Views created by code; no Compose and no XML layouts.
- Support Android mobile phones and tablets; Android TV is out of scope.
- Add no third-party dependencies.
- Keep all corners at 0 dp, text and button labels left-aligned, and touch targets at least 44 dp.
- Use the supplied Modernist design tokens, Archivo font assets, and only Lucide icons used by Phase 1.
- Keep source configuration encrypted with Android Keystore AES/GCM; never log provider URLs or credentials.
- Keep the current stableId() algorithm and its input fields unchanged.
- Keep one PlayerView; it may move between containers, but do not call play(), change the selected video, or reload its media item when changing screen or orientation.
- Until Task 6, the Ajustes tab opens the current source dialog so every intermediate build remains usable.
- Reuse one SafeNetwork/OkHttpClient instance for the active source across playback and logo requests.
- Bound the logo bitmap cache to one sixteenth of the available app heap, decode images to on-screen dimensions, and refresh programme/progress in the background or for visible rows only.
- Use the device locale for displayed dates and times.
- Use custom portrait controls with PlayerView.useController=false; use the Media3 controller only in provisional fullscreen during Phase 1.
- Stop after Phase 1 for user review; do not start Phase 2 without approval.
- JUnit is authorized only for ChannelName and local-day guide filtering; do not add other automated tests. Compile and perform manual app-level review for other behavior.

## Review Focus

- Interrupted or partially written source migration must leave the legacy encrypted source recoverable; cover this with a manual migration-failure check in Task 1.
- HTTP logos must honor that source’s authorization, fall back to initials, and explain blocked loading in Ajustes; cover allowed/blocked HTTP in Task 4.
- Channel name cleanup must preserve original names and search behavior for brand/acronym exceptions; cover representative M3U names and original-name search in Task 2's authorized JUnit tests.
- Midnight, timezone boundaries, device locale, and XMLTV gaps must not show tomorrow’s listings today or stale progress; cover local-day filtering in Task 2's authorized JUnit tests and minute refresh manually.
- Rotation during search, non-default group, and scrolled list must retain UI state and active playback; cover this in Task 7.

---

### Task 1: Multiple-source storage and safe migration

**Files:**
- Modify: `app/src/main/java/es/maciptv/android/Models.kt`
- Modify: `app/src/main/java/es/maciptv/android/SecureStore.kt`
- Modify: `app/src/main/java/es/maciptv/android/MainActivity.kt`

**Interfaces:**
- Produce `data class SavedSource(id: String, name: String, config: SourceConfig, channelCount: Int = 0, updatedAt: Long = 0L, lastChannelId: String? = null)`.
- Produce `data class SourceCollection(sources: List<SavedSource>, activeSourceId: String?)`.
- Produce `SecureStore.loadSources(): SourceCollection` and `SecureStore.saveSources(collection: SourceCollection)`; retain compatibility accessors only where migration code needs them. MainActivity owns/reuses one SafeNetwork for the active source and passes it to PlaybackController and LogoLoader.
- Change playback entry to `PlaybackController.play(channel: Channel, config: SourceConfig, network: SafeNetwork, resetRetries: Boolean = true, forceOriginal: Boolean = false)` so playback and logos share that active source's one SafeNetwork/OkHttpClient.
- Keep existing favorites and recents property values and keep `stableId()` byte-for-byte unchanged.

- [ ] **Step 1: Define source collection models and serialization**

Add stable source IDs independent of credentials, JSON serialization/deserialization for source records, and an active-source ID. Do not change `SourceConfig` URL construction or `stableId()`.

- [ ] **Step 2: Implement encrypted collection save/read and migration**

Write the new AES/GCM encrypted collection, decrypt and parse it back, and only then remove the legacy encrypted source value. If writing or verification fails, retain the legacy value and surface an error without replacing in-memory state.

- [ ] **Step 3: Preserve last-channel values per source**

Migrate the existing legacy last-channel ID into “Mi lista”. Update the active source’s ID on channel selection. Removing an active source selects another existing source without deleting its last-channel ID; removing a non-active source does not change other records.

- [ ] **Step 4: Build and inspect migration behavior**

Run `./gradlew --no-daemon assembleDebug`. Manually verify the successful legacy migration, a failed-write fallback using a controlled invalid payload or storage failure seam, preserved favorites/recents, and removal of one source while another has a saved last channel.

- [ ] **Step 5: Commit the storage milestone**

Commit only Models, SecureStore, MainActivity integration, and any related source migration notes.

### Task 2: Channel presentation data, guide day filtering, and refresh state

**Files:**
- Create: `app/src/main/java/es/maciptv/android/ChannelName.kt`
- Create: `app/src/main/java/es/maciptv/android/ChannelPresentation.kt`
- Create: `app/src/main/java/es/maciptv/android/GuideDayFilter.kt`
- Create: `app/src/test/java/es/maciptv/android/ChannelNameTest.kt`
- Create: `app/src/test/java/es/maciptv/android/GuideDayFilterTest.kt`
- Modify: `app/build.gradle.kts` to add the explicitly authorized JUnit 4 test dependency.
- Modify: `app/src/main/java/es/maciptv/android/MainActivity.kt`

**Interfaces:**
- Produce `data class ChannelPresentation(channel: Channel, displayName: String, country: String?, quality: String?, currentProgramme: Programme?, progressPercent: Int?, programmeEndLabel: String?)`.
- Produce `data class ChannelName.Result(displayName: String, country: String?, quality: String?)`, `ChannelName.parse(original: String): ChannelName.Result`, and `ChannelName.matches(channel: Channel, displayName: String, query: String): Boolean`.
- Produce pure `GuideDayFilter.forDate(programmes: List<Programme>, date: LocalDate, zone: ZoneId): List<Programme>` with a start-inclusive, next-day-start-exclusive interval.
- Produce `buildChannelPresentations(channels: List<Channel>, guide: Map<String, List<Programme>>, nowMillis: Long): List<ChannelPresentation>`; UI row binding consumes these values without parsing names or scanning guide programmes.

- [ ] **Step 1: Write the authorized ChannelName tests and verify RED**

Add JUnit tests named `cleansCountryBrandAndResolutionSuffix`, `usesCodecAsQualityWhenPresent`, and `keepsOriginalNameSearchable`. Assert the approved ES/M+/DAZN/US examples, output labels, original-name preservation, and original-form search. Add the allowed JUnit dependency. Run the focused test task and confirm failure because the production API is not implemented.

- [ ] **Step 2: Implement ChannelName and verify GREEN**

Recognize the country and quality prefixes/suffixes in the spec, preserve brands and acronyms, and return the original when cleanup is not useful. Keep `Channel.name` unchanged. Run ChannelNameTest and confirm it passes.

- [ ] **Step 3: Write the local-day filter test and verify RED**

Add `tomorrowIncludesOnlyProgrammeStartsInNextLocalDay` in GuideDayFilterTest. Use an explicit ZoneId and assert exclusion immediately before local midnight, inclusion at the next day’s start, and exclusion at the following day’s start. Confirm the test fails because the filter API is absent.

- [ ] **Step 4: Implement GuideDayFilter and verify GREEN**

Implement the local-day interval using `date.atStartOfDay(zone)` and `date.plusDays(1).atStartOfDay(zone)` so DST days need not be 24 hours. Run both authorized test classes and confirm they pass.

- [ ] **Step 5: Build cached presentation data and localized labels**

Compute names and metadata after playlist parsing. Build current programme/progress snapshots at load and refresh once per minute on the worker executor, publishing on the main thread; never do per-channel guide scans during row binding. Format date/time labels with `Locale.getDefault()` and the device time zone. Cancel refresh in `onDestroy()`.

- [ ] **Step 6: Review derived data behavior manually**

Check the rendered M3U examples and original-form search. Confirm a programme transition refreshes after one minute and verify today/tomorrow labels follow the emulator’s locale and time zone.

- [ ] **Step 7: Commit channel presentation milestone**

Commit the parser/presentation implementation and focused MainActivity wiring.

### Task 3: Design resources and reusable UI primitives

**Files:**
- Create: `app/src/main/res/font/archivo_variable.ttf` (Archivo variable font; apply weights 400, 600, and 800 in view styles)
- Create: `app/src/main/assets/fonts/Archivo-OFL.txt`
- Create: used icon vector drawables under `app/src/main/res/drawable/`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/es/maciptv/android/ui/Theme.kt`
- Create: `app/src/main/java/es/maciptv/android/ui/ViewPrimitives.kt`

**Interfaces:**
- Produce `object AndroTheme` with exact colors, text sizes, spacing, divider widths, and `fun dp(context: Context, value: Int): Int`.
- Produce `fun text(context: Context, value: CharSequence, style: TextStyle): TextView` and square-corner button/icon helpers with minimum 44 dp bounds.
- Provide Spanish `contentDescription` strings for each icon-only control implemented in Phase 1.

- [ ] **Step 1: Add licensed Archivo assets and only required icons**

Bundle Archivo weights 400, 600, and 800 as font resources and include the OFL license. Convert only icons used by Phase 1 to vector drawables. Add no icon or font library.

- [ ] **Step 2: Implement token-backed view helpers**

Map README colors and dimensions exactly. Ensure default Android Button background/tint, minimum sizes, font padding, and corner shapes do not introduce rounded corners or centered labels.

- [ ] **Step 3: Inspect resource and sizing consistency**

Run `./gradlew --no-daemon assembleDebug`; inspect the generated resource set to confirm only used vector icons are included and that all icon-only buttons have Spanish descriptions.

- [ ] **Step 4: Commit design primitives**

Commit the font, license, used icon assets, strings, and UI primitive files.

### Task 4: Safe logo loader and virtualized channel rows

**Files:**
- Create: `app/src/main/java/es/maciptv/android/ui/LogoLoader.kt`
- Create: `app/src/main/java/es/maciptv/android/ui/ChannelRow.kt`
- Modify: `app/src/main/java/es/maciptv/android/MainActivity.kt`
- Modify: `app/src/main/java/es/maciptv/android/SafeNetwork.kt` only if it needs a bounded image-fetch API that reuses its validation rules.

**Interfaces:**
- Produce `LogoLoader.load(url: String, network: SafeNetwork, target: ImageView, fallback: Drawable)` and `LogoLoader.clear()`.
- Keep an in-memory `LruCache` capped at `Runtime.getRuntime().maxMemory() / 16` bytes, accounting with `Bitmap.allocationByteCount`. Decode to target on-screen pixel dimensions, not source resolution. Image work must not run on the UI thread.
- Reuse the exact active-source SafeNetwork/OkHttpClient used by PlaybackController; create a new instance only when active source/config changes.
- Channel rows consume `ChannelPresentation` from Task 2 and expose callbacks for selection and favorite toggling.

- [ ] **Step 1: Implement bounded, cancellable logo loading**

Use existing OkHttp/SafeNetwork. Enforce the source’s HTTP rule before requesting. Bound response bytes before decode and use bounds-first decoding to cap image dimensions/memory. Guard recycled rows against late callbacks. On failure, retain initials and report a non-sensitive source warning when HTTP was blocked.

- [ ] **Step 2: Implement the channel row**

Match the 52 dp portrait logo, 38 dp landscape logo, channel/programme/progress layout, favorite affordance, selected/live states, accessibility labels, and specified divider and spacing tokens.

- [ ] **Step 3: Connect row adapter and verify network policy**

Bind cached `ChannelPresentation` values. Manually verify HTTPS logo, authorized HTTP logo, blocked HTTP logo (no request, initials, settings indication), missing logo, and recycled-row correctness.

- [ ] **Step 4: Commit row and logo milestone**

Commit LogoLoader, reusable row, and adapter changes.

### Task 5: Player continuity and portrait screens

**Files:**
- Create: `app/src/main/java/es/maciptv/android/ui/PortraitScreens.kt`
- Create: `app/src/main/java/es/maciptv/android/ui/GuideViews.kt`
- Create: `app/src/main/java/es/maciptv/android/ui/PlaybackControls.kt`
- Modify: `app/src/main/java/es/maciptv/android/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` only if the existing configuration-change handling needs adjustment.

**Interfaces:**
- Produce `PortraitScreens.renderChannels(state: ChannelsUiState, actions: ChannelsActions): View`, `renderPlayer(state: PlayerUiState, actions: PlayerActions): View`, and `renderGuide(state: GuideUiState, actions: GuideActions): View`.
- Produce `PlaybackControls` custom portrait play/pause, progress, back, and fullscreen controls. Bind `PlayerView.useController = false` outside fullscreen and enable the Media3 controller only in provisional fullscreen.
- Reparent the same PlayerView as needed; preserve the selected video and never call play() or reload the media item during screen changes.

- [ ] **Step 1: Create bottom navigation and Channels screen**

Implement the preview, source header, search open/close, horizontal group chips, channel list, loading/empty states, and bottom tabs. Until Task 6, the Ajustes tab invokes the current source dialog. Preserve source/group/search state while rebuilding views.

- [ ] **Step 2: Create portrait player and guide screens**

Implement custom portrait transport controls, metadata/favorite/actions/guide table, guide channel and local-day selectors, and “Ver canal”. Keep reminders as a Phase 3 placeholder only.

- [ ] **Step 3: Move the same player view on navigation and rotation**

Reparent the existing PlayerView between screen containers where necessary without calling playback.play(), changing the selected video, or reloading its media item. Save and restore selected tab, group, search text, and ListView first-visible position/offset. Confirm portrait/landscape and preview/player transitions preserve playback.

- [ ] **Step 4: Compile and inspect portrait flows**

Run `./gradlew --no-daemon assembleDebug`. Manually navigate Channels → Player → Channels, switch tabs, rotate during active playback, and confirm playback stays on the same channel with list position and UI state retained.

- [ ] **Step 5: Commit portrait screen milestone**

Commit portrait screen builders, custom controls, and player continuity integration.

### Task 6: Settings, provider sheet, multi-source actions, and sleep timer

**Files:**
- Create: `app/src/main/java/es/maciptv/android/ui/SettingsScreen.kt`
- Create: `app/src/main/java/es/maciptv/android/ui/ProviderSheet.kt`
- Create: `app/src/main/java/es/maciptv/android/ui/ConfirmationDialog.kt`
- Create: `app/src/main/java/es/maciptv/android/ui/NoticeView.kt`
- Modify: `app/src/main/java/es/maciptv/android/SecureStore.kt`
- Modify: `app/src/main/java/es/maciptv/android/MainActivity.kt`

**Interfaces:**
- Produce `SettingsScreen.render(state: SettingsUiState, actions: SettingsActions): View`.
- Produce `ProviderSheet.show(kind: SourceKind, existing: SavedSource?, onSave: (SavedSource) -> Unit)`.
- Produce a main-thread `SleepTimer` with `setMinutes(minutes: Int)`, `cancel()`, and `onExpire`; do not persist timer state.

- [ ] **Step 1: Implement source list and add-source grid**

Show active source, kind/count/updated time, HTTP logo warning where applicable, refresh/remove actions, and URL M3U/file/Xtream/demo add choices. Selecting a source changes active source and loads its saved channel without clearing other source records.

- [ ] **Step 2: Implement provider configuration and safe confirmation flows**

Build the square-corner bottom sheet with exact URL M3U/Xtream fields, HTTP/local checkboxes, Spanish errors, file picker, and cancel/save footer. Implement source removal and external playback confirmation without rendering credentials.

- [ ] **Step 3: Implement playback preferences and timer lifecycle**

Persist resume-last-channel and fit/fill. Implement No/30/60/90 minute options, cancel when selecting No, when destroying the activity, and before removing the active source; pause when elapsed. Never restore an in-progress timer after process/app restart.

- [ ] **Step 4: Integrate source migration failure handling and verify settings**

Ensure source migration is write-then-read-verify-then-delete. Manually check source add/switch/refresh/remove, saved last channels on multiple sources, active source removal, timer expiration/cancel, and absence of secrets in visible errors.

- [ ] **Step 5: Compile and commit settings milestone**

Run `./gradlew --no-daemon assembleDebug`, then commit settings, provider, dialog, notice, persistence, and timer changes.

### Task 7: Landscape panels, provisional fullscreen, and Phase 1 integration

**Files:**
- Create: `app/src/main/java/es/maciptv/android/ui/LandscapePanels.kt`
- Modify: `app/src/main/java/es/maciptv/android/MainActivity.kt`
- Modify: `app/src/main/java/es/maciptv/android/ui/PlaybackControls.kt`
- Modify: `README.md`
- Modify: `CLAUDE.md`

**Interfaces:**
- Produce `LandscapePanels.render(state: LandscapeUiState, actions: LandscapeActions, playerHost: View): View` with 170 dp / 250 dp reference columns and flexible player column.
- Reuse the same actions, source data, channel presentations, timer, and PlayerView as portrait; do not create a second player/controller.

- [ ] **Step 1: Implement landscape header, groups, channels, and player actions**

Use accent header, search, source settings/refresh, group counts, virtualized channel rows, and player area with current/next programme. Wire every visible action to the existing coordinator.

- [ ] **Step 2: Preserve provisional fullscreen behavior and icon accessibility**

Keep orientation request, hidden system bars, back/exit, fill/fit, current Media3 fullscreen controller, and all Spanish icon descriptions. Do not introduce Phase 2 brightness/volume/lock/drawer UI yet. In Phase 2, capture system brightness on fullscreen entry and restore it on every exit and activity-destruction path.

- [ ] **Step 3: Final Phase 1 build and manual acceptance review**

Run `./gradlew --no-daemon assembleDebug`. On the emulator, review portrait Channels/Player/Guide/Settings, landscape panels, source migration/settings, favorite/search/group behavior, tomorrow filtering, logo policy, rotation state, sleep timer, external warning, and provisional fullscreen. Resolve regressions before declaring Phase 1 ready for user review.

- [ ] **Step 4: Update handoff documents and commit Phase 1**

Update README.md and CLAUDE.md with the shipped Phase 1 structure, build steps, known limitations, and the Phase 2/3 boundary. Commit the integrated Phase 1 only after the manual review is clean.

## Phase 1 review gate

After Task 7, present the changes and build result to the user and wait for explicit approval before any Phase 2 work. Do not implement brightness restoration until the Phase 2 fullscreen overlay is introduced; then restore the captured system brightness on every fullscreen exit and activity destruction path.
