# System Architecture

This document describes the high-level architecture of the Android advertising SDK in this repository. The Gradle root project is named **DMA**; published artifacts use the group `tech.done.ads` (version `0.1.0-SNAPSHOT` unless overridden).

## Purpose

The SDK provides **VMAP/VAST–driven linear ad playback** with scheduling, tracking, and player integration. The recommended integration path is **Google IMA–style usage with AndroidX Media3**: the host application owns the content `ExoPlayer`, while the SDK manages a separate ad player, overlays, and lifecycle coordination inside an `AdDisplayContainerView`.

## Module Layers

The codebase is organized into Gradle subprojects with a clear dependency direction:

| Layer | Modules | Role |
|--------|---------|------|
| **Foundation** | `network` | Pluggable HTTP for ad tags, VAST fetches, and tracking beacons |
| **Parsing & scheduling** | `parser`, `scheduler` | XML parsing and VMAP → timeline (`AdTimeline`) |
| **Execution** | `core` | `DefaultAdEngine` orchestrates playback, breaks, and tracking |
| **Player abstraction** | `player-common` | `PlayerAdapter`, `PlayerState`, event listeners |
| **Integrations** | `player-media3`, `player-exoplayer2` | Media3 IMA-like bridge vs. legacy ExoPlayer2 adapter |
| **UI (optional)** | `ui-compose` | Jetpack Compose overlay primitives (not wired into `player-media3` by default) |
| **Demo** | `sample-app` | Sample host using `Media3AdsLoader` and Compose shell |

## Data Flow (Media3, IMA-like)

1. **Ad tag load**  
   `Media3AdsLoader.requestAds(adTagUri)` uses `NetworkLayer.get` to retrieve XML. The loader inspects the root element to classify **VMAP** vs **VAST**. Raw VAST is wrapped as a synthetic preroll-only VMAP for a uniform pipeline.

2. **VMAP ingest**  
   `DefaultAdEngine.loadVMAP(xml)` parses with `VMAPPullParser`, then `VMAPScheduler.buildTimeline` produces `AdTimeline` (preroll, midroll, postroll `ScheduledBreak` entries).

3. **Break execution**  
   For each break, the engine resolves VAST (inline or via URI + `NetworkLayer`), selects creatives, and calls `PlayerAdapter.playAd` / `resumeContent`. **Media3** path: `Media3ImaLikePlayerAdapter` swaps between content `ExoPlayer` and a dedicated ad `ExoPlayer`, hosts `PlayerView` and (unless disabled) `AdOverlayView` under `AdDisplayContainerView`. The engine’s wait for the end of a linear ad completes on `PlayerListener.onAdEnded` **or** when `PlayerState.isInAd` becomes `false` (user skip / `resumeContent` without the ad player reaching `STATE_ENDED`).

4. **Position & midrolls**  
   The engine observes `PlayerState.contentPositionMs` (and a periodic tick) to fire midrolls when `triggerTimeMs` is reached. Postrolls trigger on content end (`PlayerListener.onContentEnded`).

5. **Tracking**  
   `TrackingEngine.track` fires VAST tracking URLs (quartiles, complete, etc.). `RetryingTrackingEngine` implements retries with backoff over `NetworkLayer`.

6. **Markers (optional)**  
   After VMAP load, `Media3AdsLoader` may reflect midroll times on `PlayerView`’s progress bar via reflection (`setAdGroupTimesMs`), when `setAdMarkersContainerView` is set.

## Dependency Graph (Logical)

```
sample-app
  → player-media3, core, parser, scheduler, tracking, network, player-common, ui-compose

player-media3
  → core, parser, scheduler, tracking, network, player-common
  → Media3 (ExoPlayer, common, UI)

player-exoplayer2
  → player-common
  → ExoPlayer2

core
  → parser, scheduler, tracking, network, player-common

parser
  → network, kXML2 (XmlPull)

scheduler
  → parser, network

tracking
  → network

ui-compose
  → Compose BOM, Material3 (no dependency on core/player)
```

`network` is the only module with **no** project dependencies on other SDK modules (aside from sharing `AdSdkLogConfig` in package `tech.done.ads`).

## Key Runtime Components

- **`Media3AdsLoader`**  
  Facade: wires `DefaultAdEngine`, parsers, scheduler, tracking, and `Media3ImaLikePlayerAdapter`. Prefer **`Media3AdsLoader.builder(context)`** (fluent `network`, `tracking`, `scope`, `debugLogging`) over the deprecated primary constructor. Exposes `StateFlow` `isAdPlaying`, `StateFlow` `playerState`, optional `AdsEventListener` multicasting, ad playback callbacks, `setShowBuiltInAdOverlay`, and `skipCurrentAd()`.

- **`DefaultAdEngine`**  
  Single orchestration point for timeline lifecycle, VAST resolution, skip handling, and coordination with `PlayerAdapter` and `TrackingEngine`. Propagates SDK callbacks through **`dispatchAdsEvent`** with **`AdsEventKind`** and **`AdsEventPayload`** so analytics and UI stay aligned with a single event taxonomy.

- **`PlayerAdapter`**  
  Abstract boundary so the same engine can drive Media3 (dual player) or ExoPlayer2 (single player swapping `MediaItem`).

## Threading and Configuration

- **`NetworkLayer.dispatcher`** is used for I/O (ad tag fetch, tracking).  
- Engine and Media3 rebuild paths expect **main-thread** configuration for `Media3AdsLoader` / `AdDisplayContainerView`.  
- **`AdSdkLogConfig.isDebugLoggingEnabled`** gates verbose logging across modules; `Media3AdsLoader` can enable it via **`Builder.debugLogging(...)`** (or the deprecated constructor).

## Extension Points

- Implement **`NetworkLayer`** with OkHttp, Ktor, or other stacks.  
- Swap **`VMAPParser` / `VASTParser` / `AdScheduler`** when constructing `DefaultAdEngine` for tests or alternate scheduling rules.  
- Use **`AdsEventListener`** for analytics or custom UI driven by SDK events (**`Media3AdsLoader.addAdSdkEventListener`** takes this type; default no-op methods mean you override only the callbacks you need). Types **`AdsSchedulingListener`** and **`AdsCreativePlaybackListener`** document the two concern areas; the engine dispatches via **`AdsEventKind`**.  
- **`ui-compose`** supplies **`AdOverlay`** / **`AdUiState`** / **`AdUiStyle`** / **`overrideContent`** for apps that render ad chrome in Compose. Use **`Media3AdsLoader.setShowBuiltInAdOverlay(false)`** and **`playerState`** to drive it; **`skipCurrentAd()`** triggers **`resumeContent`** on the adapter.

## Related Reading

Module-level detail is in the [documentation index](../README.md#documentation). For ready-made prompts to draw SDK flow diagrams (Mermaid or image AI), see [diagram-generation-prompts.md](diagram-generation-prompts.md).
