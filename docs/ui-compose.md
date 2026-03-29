# Module: `ui-compose`

## Overview

The **ui-compose** module provides **Jetpack Compose** building blocks for ad chrome (countdown, skip affordance, optional ad index labeling). It has **no** dependency on **core**, **player-media3**, or **player-common** — you map your own state into **`AdUiState`** and wire **`onSkip`** to your player integration.

For **Media3**, the usual pairing is:

- **`Media3AdsLoader.setShowBuiltInAdOverlay(false)`** — suppresses the default **`AdOverlayView`** inside **`AdDisplayContainerView`**.
- **`Media3AdsLoader.playerState`** — **`StateFlow<PlayerState>`**; map fields such as **`isInAd`**, **`adPositionMs`**, **`adDurationMs`**, **`adSkipOffsetMs`** into **`AdUiState`**.
- **`Media3AdsLoader.skipCurrentAd()`** — ends the current linear ad and resumes content (implements **`PlayerAdapter.resumeContent()`** on the main thread).

See **`sample-app`** **`SampleComposeAdOverlay.kt`** for a full example (including TV-oriented focus on the skip control).

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **AdUiState** | Immutable state: **`visible`**, **`canSkip`**, **`skipInSeconds`**, **`remainingSeconds`**, optional **`adIndex`** / **`adCount`**. |
| **AdUiStyle** | Theming: scrim, text, and accent colors as Compose **`Color`** values (applied to the default layout and to the scrim behind **`overrideContent`**). |
| **AdOverlay** | Full-screen overlay: default layout (bottom-start metadata, bottom-end skip **Button**) or **`overrideContent: @Composable (AdUiState) -> Unit`** for a fully custom layout. Includes **Preview** composables. |

## Dependencies

- **Third-party:** Compose BOM, Compose UI, Material3, Timber

## Integration / Usage

### Default layout + theme

```kotlin
@Composable
fun MyPlayerChrome(adUi: AdUiState, onSkip: () -> Unit) {
    AdOverlay(
        state = adUi,
        onSkip = onSkip,
        style = AdUiStyle(
            scrimColor = Color(0x66000000),
            textColor = Color.White,
            accentColor = Color(0xFF00C853),
        ),
    )
}
```

### Custom layout (`overrideContent`)

Pass **`overrideContent`** to replace inner content. The outer **`Box`** still applies **`modifier`**, **`fillMaxSize`**, and **`style.scrimColor`**. Your **`onSkip`** lambda is not wired automatically into **`overrideContent`** — call it from your own button **`onClick`**.

### Mapping `PlayerState` → `AdUiState`

**`PlayerState`** (from **`player-common`**) exposes **`isInAd`**, **`adPositionMs`**, **`adDurationMs`**, **`adSkipOffsetMs`**. Typical rules:

- **`visible = isInAd`**
- **`canSkip = adSkipOffsetMs != null && adPositionMs >= adSkipOffsetMs`**
- **`skipInSeconds`** / **`remainingSeconds`**: derive from offsets and duration with **`ceil((ms) / 1000.0).toInt()`** (match the built-in **`AdOverlayView`** behavior if you want parity).

## Related documentation

- **[player-media3](player-media3.md)** — **`Media3AdsLoader`** APIs (**`playerState`**, **`setShowBuiltInAdOverlay`**, **`skipCurrentAd`**).
- **[sample-app](sample-app.md)** — runnable Compose + Media3 demo.
