# AndroIPTV Phase 2 Fullscreen Implementation Plan

> **For agentic workers:** Use the approved redesign spec and implement the tasks in order. Keep one commit per task.

**Goal:** Replace the provisional Media3 fullscreen toolbar with the approved dark controls, channel drawer, brightness and volume sliders, and lock mode.

**Architecture:** `MainActivity` keeps playback and navigation state. A focused `FullscreenOverlay` owns fullscreen rendering, transient visibility, and the virtualized channel drawer. `MainActivity` supplies cached channel and programme data and the existing actions. The existing `PlayerView` is moved into the overlay without changing its media item.

**Tech Stack:** Kotlin, Android Views, Media3 `PlayerView`, existing Archivo font and app vector assets. No new dependency.

**Spec:** `docs/superpowers/specs/2026-10-14-androiptv-redesign-design.md`, Phase 2; ZIP handoff README section 9.

## Global constraints

- Keep Kotlin programmatic Views and one `PlaybackController` and `PlayerView`.
- No playback restart on fullscreen entry or exit.
- Keep system bars hidden in fullscreen and preserve system back behavior.
- Minimum 44 dp icon touch areas and Spanish content descriptions.
- Restore the exact prior window brightness override on every fullscreen exit and activity destruction path.
- Use cached channel presentations for programme labels and a virtualized drawer for large lists.
- Do not add or run unit tests; earlier authorization limited JUnit to ChannelName and guide day filtering. Compile and review on the emulator.

## Task 1: Fullscreen controls and transient visibility

**Files:** Create `app/src/main/java/es/maciptv/android/ui/FullscreenOverlay.kt`; add only used vectors to `app/src/main/res/drawable/`.

- [ ] Build a full-screen `FrameLayout` with the existing `PlayerView` behind dark top/bottom gradients, a 44 dp back and channel button, channel/programme heading, central previous/play/next controls with names, live progress, and six 44 dp action cells.
- [ ] Implement visible/hidden/locked states: tapping video toggles controls, playing schedules a four-second hide, pausing keeps them visible, opening the drawer suspends hiding, locking leaves only an unlock affordance.
- [ ] Add vertical brightness and volume controls with touch/drag adjustment, and accessible Spanish descriptions.
- [ ] Compile with `./gradlew --no-daemon assembleDebug`.
- [ ] Commit the standalone UI component and assets.

## Task 2: Integrate playback, settings, and brightness lifecycle

**Files:** Modify `app/src/main/java/es/maciptv/android/MainActivity.kt`.

- [ ] Route fullscreen `buildUi()` to the new overlay, reparenting the existing `PlayerView` and setting `useController=false` without calling `play()` or changing media.
- [ ] Wire prior/next/last, play/pause, favorite, timer, fit/fill, Cast placeholder, and exit. Update labels after channel change and once-per-minute programme refresh.
- [ ] Capture the current `window.attributes.screenBrightness` on fullscreen entry. Set only the window override while dragging; restore the captured value on exit and `onDestroy()`.
- [ ] Keep landscape orientation, immersive bars, and back/gesture exit.
- [ ] Compile and commit the integration.

## Task 3: Virtualized channel drawer and finishing review

**Files:** Complete `FullscreenOverlay.kt`, `MainActivity.kt`, `README.md`, and `CLAUDE.md`.

- [ ] Show a 300 dp right drawer with current group, close button, virtualized dark rows, cached programme subtitles, current-channel highlight, and selection without exiting fullscreen.
- [ ] Preserve visible channel filtering, group, and search. Update previous/next names and selected row after switching channels.
- [ ] Review the screen in the running emulator, fix any layout or navigation issues, and compile again.
- [ ] Document shipped Phase 2 and remaining Phase 3 work. Commit the final task.

After the phase is reviewed, stop before Phase 3 as required by the approved spec.
