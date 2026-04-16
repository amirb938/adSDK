# Module: `sample-app`

## Overview

**sample-app** is an **Android application** module that demonstrates end-to-end integration of **`Media3AdsLoader`** with **Jetpack Compose** (**`ComponentActivity`**, **`setContent`**). It launches into a **Main Menu** (TV-friendly focus) so you can quickly test different SDK scenarios (VAST, VMAP, custom UI, SIMID overlays). It loads ad XML from **assets**, enables **Timber** logging, and registers **`SampleAdsEventLogger`** as an **`AdsEventListener`**.

Some scenarios use **Compose for the ad overlay** instead of the default View overlay:

- **`Media3AdsLoader.setShowBuiltInAdOverlay(false)`** — built-in **`AdOverlayView`** is not attached.
- **`playerState`** is collected from **`adsLoader.playerState`** and passed into **`SampleCustomAdOverlay`** (**`ui-compose`** **`AdOverlay`** with **`overrideContent`**).
- Skip is wired to **`adsLoader.skipCurrentAd()`**.

**`SampleCustomAdOverlay`** targets **TV-style** use: skip stays in the focus order (**`TextButton`** remains enabled for focus; **`onClick`** only skips when **`canSkip`**), **`FocusRequester`** + **`LaunchedEffect`** request default focus on the skip control, and **`focusGroup()`** scopes D-pad navigation on the top bar.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`MainActivity`** | Hosts a lightweight Compose navigation state and switches between **Main Menu** and player scenario screens. Main menu requires double-back to exit. |
| **`MainMenuScreen`** | Vertical button list for scenarios; focus styling for Android TV. |
| **`tech.done.ads.sample.player.*`** | Scenario screens implemented with `Media3AdsLoader` and Compose UI. |
| **`ExternalPlayerActivity`** | Demonstrates `AdsLoader.createWithExternalPlayer(...)`: the app uses Media3 internally but treats the SDK as if it were integrating with a non-Media3 player by mapping SDK commands to player calls and reporting state/events back through `ExternalPlayerAdapter`. |
| **`SampleComposeAdOverlay.kt`** | **`playerStateToAdUiState`**, **`SampleCustomAdOverlay`** — maps **`PlayerState`** (including **`isAdSkippable`**: no skip affordance when the creative is not skippable), themed **`AdUiStyle`**, custom top bar via **`overrideContent`**; skip **`FocusRequester`** only when skip UI is shown. |
| **`SampleApplication`** | Application class (Timber tree setup if configured). |
| **`SampleNetworkLayer`** | App-level **`NetworkLayer`** for real device fetches when not using the loader’s **`SampleNetworkLayer`**. |
| **`SampleAdsEventLogger`** | Example **`AdsEventListener`** logging SDK callbacks. |
| **`SampleConfig`** | Central place for shared constants (content URL and asset names). |
| **`assets/sample_vmap.xml`** | Sample VMAP payload for local testing. |
| **`assets/sample_vast.xml`** | Sample VAST payload; wrapped into a synthetic preroll-only VMAP by the sample UI. |
| **`assets/vmap_simid.xml`** | VMAP with inline VAST that contains a SIMID `InteractiveCreativeFile` pointing to a local HTML overlay. |
| **`assets/vmap_simid_no_skip.xml`** | Same as SIMID VMAP, but without `skipoffset` (non-skippable). |
| **`assets/simid_overlay.html`** | SIMID mock overlay (form) that can request pause/play via `AndroidSimidBridge.postMessage`. |
| **`assets/simid_overlay_qr.html`** | SIMID mock overlay (QR panel) that pauses on “enter number” and resumes on “send message”. |

## Dependencies

- **Project modules:** `core`, `parser`, `scheduler`, `tracking`, `network`, `player-common`, `player-media3`, `ui-compose`
- **Third-party:** Media3, Compose, Material3, Activity Compose, Lifecycle, Coroutines, Timber

## Integration / Usage

This module is not published as a library. Run it from Android Studio.

Use it as a reference for **`Media3AdsLoader`** wiring, **`playerState`** + **`ui-compose`**, asset-based VMAP testing, and **`AdsEventListener`** usage.

## Related documentation

- **[ui-compose](ui-compose.md)** — **`AdOverlay`** / **`AdUiState`**.
- **[player-media3](player-media3.md)** — loader API surface.
