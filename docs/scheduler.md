# Module: `scheduler`

## Overview

The **scheduler** module converts a parsed **VMAP** response into an **`AdTimeline`**: ordered lists of **`ScheduledBreak`** instances for preroll, midroll, and postroll. It encodes **when** each break should fire (for midrolls) and **what** VAST source to use (URI or inline XML).

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`AdScheduler`** | Interface with **`buildTimeline(vmap: VMAPResponse): AdTimeline`**. |
| **`VMAPScheduler`** | Default implementation: maps **`AdBreak.position`** (`Preroll`, `Midroll`, `Postroll`) to **`ScheduledBreak`**, validates midroll offsets, sorts midrolls by time, skips breaks without VAST source. |
| **`AdTimeline`** | Holds **`preroll`**, **`midrolls`**, **`postroll`** lists of **`ScheduledBreak`**. |
| **`ScheduledBreak`** | Break id, original **`AdBreak`**, **`triggerTimeMs`** (null for postroll), **`vastAdTagUri`**, **`vastInlineXml`**. |

## Dependencies

- **Project modules:** `parser`, `network`
- **Third-party:** Timber

## Integration / Usage

The **`DefaultAdEngine`** calls **`scheduler.buildTimeline(parsed)`** after **`loadVMAP`**. Any implementation of **`AdScheduler`** can be injected for testing or alternate VMAP interpretation:

```kotlin
DefaultAdEngine(
    scheduler = VMAPScheduler(),
    // ...
)
```

**`Media3AdsLoader`** also instantiates **`VMAPScheduler`** when computing seek-bar ad group times from VMAP XML.
