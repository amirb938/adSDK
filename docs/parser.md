# Module: `parser`

## Overview

The **parser** module implements **streaming (pull) parsing** of **VMAP** and **VAST** XML into Kotlin domain models. It is designed to be used by the ad engine and scheduler without binding to a specific HTTP client; URL fetching for wrapped VAST is modeled as an optional **`VASTWrapperFetcher`** callback.

## Key Components

| Symbol | Responsibility |
|--------|----------------|
| **`VMAPParser` / `VMAPPullParser`** | Parse VMAP XML strings into **`VMAPResponse`** (version, ad breaks, positions, offsets, inline or URI VAST sources). |
| **`VASTParser` / `VASTPullParser`** | Parse VAST XML into a list of **`VASTAd`** (creatives, media files, tracking URLs, skip offsets, wrappers). Also extracts **`<InteractiveCreativeFile apiFramework="SIMID">…</InteractiveCreativeFile>`** under `<Linear>` into `VASTAd.interactiveCreativeUrl` / `VASTAd.interactiveApiFramework`. Supports async wrapper resolution via **`VASTWrapperFetcher`**. |
| **`PullParserFactory`** | Internal **`XmlPullParser`** setup (kXML2 / XmlPull). |
| **`VMAPResponse`, `VASTAd`, `AdBreak`, `SkipOffset`, etc.** | Domain models in **`tech.done.ads.parser.model`**. |
| **`XmlParseError`** | Structured parse failure reporting. |
| Internal **`TimeParsing`**, **`XmlPull`** | Time offset and low-level XML utilities. |

## Dependencies

- **Project modules:** `network` (shared logging configuration; no hard dependency on HTTP from parser types themselves)
- **Third-party:** kXML2 (`org.xmlpull`), Timber

## Integration / Usage

Implementations are passed into **`DefaultAdEngine`**:

```kotlin
DefaultAdEngine(
    vmapParser = VMAPPullParser(),
    vastParser = VASTPullParser(),
    // ...
)
```

**`Media3AdsLoader`** uses the same default pull parsers. **`Media3AdsLoader.requestAds`** may also use **`VMAPPullParser`** and **`VMAPScheduler`** to derive midroll marker positions for the seek bar without going through the engine.
