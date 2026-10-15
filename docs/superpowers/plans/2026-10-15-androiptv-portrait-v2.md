# AndroIPTV Portrait v2 Implementation Plan

> **For agentic workers:** Implement the three blocks in order with one commit per block. Use `superpowers:executing-plans` for inline execution.

**Goal:** Deliver the v2 portrait player, two-step Channels, and multi-channel EPG while preserving landscape panels and fullscreen.

**Architecture:** `MainActivity` retains navigation and playback state. A shared `ProgrammeSchedule` calculation supplies player rows and a custom `EpgGridView`. A shared `GroupListView` renders the same list selection in Channels and Guide. Portrait-only renderers keep existing landscape `GuideViews` and `LandscapePanels` untouched.

**Tech Stack:** Kotlin Android Views, Canvas, Media3, existing SecureStore and logo loader. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-15-androiptv-portrait-v2.md` (copy of the user-supplied `CAMBIOS-v2.md`), plus the user's reminder clarification of 2026-10-15.

## Global constraints

- One `PlayerView` without calling `play()` for navigation or rotation.
- `stableId()` unchanged; display names only; locale-aware dates.
- Portrait-only changes; horizontal panels and fullscreen keep their existing UI.
- Reminder keys are `sourceId|channelId|startMillis`, persisted as `Set<String>` in `SecureStore`; no Android notifications yet. Confirm with “Marcado en la guía” / “Marca quitada”. Prune ended programme marks when loading guide.
- Use at least 44 dp touch targets, Spanish accessibility descriptions, and existing square visual tokens.
- Compile after each block; inspect the requested acceptance behaviors on the emulator. Do not add JUnit outside the previously authorized ChannelName/day-filter scope.

## Block C — portrait player

**Files:** `PortraitScreens.kt`, `MainActivity.kt`, new `ProgrammeSchedule.kt`, `SecureStore.kt`, resource vectors as needed.

- [ ] Add the shared day/channel programme calculation and persistent reminder storage/cleanup seam.
- [ ] Keep the 16:9 video fixed; add overlaid back/fullscreen, live progress, touch-to-pause, and a paused play affordance.
- [ ] Put metadata, favorite, four action cells and today’s programme rows in a vertical scroll area below the video; future reminders use shared keys and notices.
- [ ] Preserve selected channel and scroll state when closing player; compile and commit C.

## Block A — two-step Channels

**Files:** `PortraitScreens.kt`, `MainActivity.kt`, new `GroupListView.kt`, two used vectors.

- [ ] Add `portraitStep` and one virtualized group-list component; remove the group carousel in portrait.
- [ ] Show all-list search results at GROUPS and scoped results at CHANNELS; keep shared group state and correct header/kicker/back behavior.
- [ ] Preserve list position, group, search and step across rotation; implement system back and tab reselect; compile and commit A.

## Block B — multi-channel Guide

**Files:** new `EpgGridView.kt`, `PortraitGuideScreen.kt`, `ProgrammeDetailSheet.kt`, `MainActivity.kt`, `SecureStore.kt` as needed, docs.

- [ ] Add GROUPS→GRID navigation with shared `GroupListView`, days −1..3 constrained by available XMLTV and locale labels.
- [ ] Precompute a clipped, sorted day schedule off the UI thread; draw only visible programme blocks, hours and fixed channel/hour headers in one Canvas view. Use `OverScroller`, `GestureDetector`, and virtual accessibility nodes.
- [ ] Add now-line and initial/Now scrolling; show programme details above the grid, reminder toggle and watch action.
- [ ] Reuse reminder state and programme schedule with the player, prune ended marks at load, update docs, inspect acceptance criteria, compile and commit B.

## Review focus

- Very large groups and guides must create only visible rows/blocks.
- A programme crossing the 06:00 or 24:00 boundary clips correctly without changing its real times.
- Past programmes never show DIRECTO or active styling.
- Group/search/scroll state survives player navigation and rotation.
- Reminder marks of other sources survive cleanup for the active source.

The 60 fps criterion requires a mid-range physical device; the emulator can establish functional behavior and flag obvious jank, but cannot prove that hardware target.
