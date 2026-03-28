# Module: `ui-compose`

## Overview

The **ui-compose** module provides **Jetpack Compose** UI building blocks for ad chrome (countdown, skip affordance, and ad index labeling). It is **decoupled** from **core** and **player-media3**: it does not drive playback. Host applications can map engine or player state into **AdUiState** and render **AdOverlay** for a Compose-first UI, while the default Media3 path continues to use the traditional **AdOverlayView** inside **player-media3**.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **AdUiState** | Immutable state: visibility, skip eligibility, countdown fields (**skipInSeconds**, **remainingSeconds**), ad index and count. |
| **AdUiStyle** | Theming: scrim, text, and accent colors as Compose **Color** values. |
| **AdOverlay** | Composable full-screen overlay with default layout (bottom-start metadata, bottom-end skip **Button**) and optional **overrideContent** for fully custom layouts. Includes **Preview** composables for design-time use. |

## Dependencies

- **Third-party:** Compose BOM, Compose UI, Material3, Timber

There is no dependency on **player-common**, **core**, or Media3.

## Integration / Usage

```kotlin
@Composable
fun MyPlayerChrome(adUi: AdUiState, onSkip: () -> Unit) {
    AdOverlay(
        state = adUi,
        onSkip = onSkip,
        style = AdUiStyle(),
    )
}
```

Map fields from your own state machine or from **AdsEventListener** and **PlayerState** as needed. The **sample-app** module declares **ui-compose** as a dependency for optional experimentation; wiring is not required for the default demo.
