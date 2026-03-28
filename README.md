# AdSDK — IMA-style ads for Android (Media3)

Android library suite for **VMAP/VAST**-driven **linear video ads** with **scheduling**, **beacon tracking**, and an **Interactive Media Ads (IMA)–like** integration on **AndroidX Media3**. Your application **retains ownership of the content player**; the SDK manages ad playback, optional seek-bar markers, and an in-layout **ad display container** with built-in skip and countdown UI.

The Gradle root project is named **DMA**. Published coordinates use the group **`tech.done.ads`** (see `build.gradle.kts` for the current version).

## Features

- **VMAP and VAST** via pull parsers and shared domain models
- **Timeline scheduling** (preroll, midroll, postroll) from VMAP
- **Pluggable HTTP** through **`NetworkLayer`**
- **VAST tracking** with a default **retrying** implementation
- **Media3 path (recommended):** **`Media3AdsLoader`**, **`AdDisplayContainerView`**, dual-player ad/content handling, **`StateFlow`** **`isAdPlaying`**
- **Ad tag convenience:** **`requestAds(adTagUri)`** with automatic VMAP vs VAST detection; raw VAST wrapped as a synthetic preroll-only VMAP
- **Optional Compose UI module** (**`ui-compose`**) for custom ad overlays independent of the default View-based overlay
- **ExoPlayer2 adapter** for legacy single-player integrations (**`ExoPlayer2Adapter`**)

## Modules

| Module | Description |
|--------|-------------|
| **core** | **`DefaultAdEngine`** — orchestrates timeline, VAST, **`PlayerAdapter`**, and tracking |
| **parser** | VMAP/VAST pull parsers and models |
| **scheduler** | VMAP → **`AdTimeline`** (**`VMAPScheduler`**) |
| **tracking** | **`TrackingEngine`**, **`RetryingTrackingEngine`** |
| **network** | **`NetworkLayer`**, **`NetworkResponse`**, **`AdSdkLogConfig`** |
| **player-common** | **`PlayerAdapter`**, **`PlayerState`**, **`AdsEventListener`**, multicaster |
| **player-media3** | **`Media3AdsLoader`**, **`AdDisplayContainerView`**, Media3 integration |
| **player-exoplayer2** | **`ExoPlayer2Adapter`** for ExoPlayer2 |
| **ui-compose** | **`AdOverlay`**, **`AdUiState`**, **`AdUiStyle`** (Jetpack Compose) |
| **sample-app** | Demo app (Compose shell, asset VMAP, event logging) |

## Documentation

In-depth technical documentation lives under **`docs/`**:

- **[System architecture](docs/architecture.md)** — layers, data flow, dependency graph, threading, extension points
- **[core](docs/core.md)** — ad engine
- **[parser](docs/parser.md)** — VMAP/VAST parsing
- **[scheduler](docs/scheduler.md)** — timeline building
- **[tracking](docs/tracking.md)** — tracking engines and events
- **[network](docs/network.md)** — HTTP abstraction and logging config
- **[player-common](docs/player-common.md)** — player boundary types
- **[player-media3](docs/player-media3.md)** — Media3 IMA-like integration
- **[player-exoplayer2](docs/player-exoplayer2.md)** — ExoPlayer2 adapter
- **[ui-compose](docs/ui-compose.md)** — Compose overlay primitives
- **[sample-app](docs/sample-app.md)** — sample application module

## Limitations compared with Google IMA SDK

The following capabilities exist in Google’s **Interactive Media Ads (IMA) SDK** for Android but are **not** part of this project’s scope today. AdSDK is deliberately **lightweight**, **open source**, and **host-owned** (you control the player, network stack, and UI surface), which trades breadth of ad formats and measurement integrations for smaller binary surface and full control of the playback path.

- **Non-linear ads and overlays** — Only **linear video** creatives are targeted. Non-linear VAST (overlays, graphics timed to content, etc.) is not implemented.

- **Interactive creatives (VPAID / SIMID)** — There is **no** VPAID or SIMID runtime: interactive or script-driven creatives are out of scope; playback assumes progressive/downloadable **MP4** (or other formats your **ExoPlayer** stack can decode), not executable ad units.

- **Companion ads** — **CompanionBanner** (and related) elements are **not** parsed for layout, and the SDK does **not** render companion slots beside or around the player.

- **Viewability and Open Measurement (OM)** — **OMSDK** is **not** integrated. Viewability and third-party verification workflows that depend on Google’s measurement stack are not provided; you may layer your own analytics or MRC-aligned tooling outside this library.

- **Dynamic ad insertion (DAI) / server-side ad insertion (SSAI)** — **DAI** manifests, **pod serving**, and stitched **SSAI** stream workflows tied to IMA’s DAI APIs are **not** supported. This SDK expects **client-side** VMAP/VAST with distinct ad media URLs.

- **Ad tag macro expansion** — IMA expands a large set of **dynamic macros** (device, playback, privacy/consent such as **TCF** signals, cache busters, etc.) on the client. AdSDK **does not** replicate that engine: URLs are fetched largely **as given**. You can **pre-substitute** macros in your ad tag URL or wrap **`NetworkLayer`** if you need custom expansion rules.

- **Ad podding (VAST vs VMAP)** — **Multiple VMAP ad breaks** at a given phase (for example, several preroll breaks) are played **one after another**. Within a **single VAST response**, the engine selects the **first** linear ad that has a usable **MediaFile** and does **not** walk a full **VAST pod** (every `<Ad>` in sequence, **`AdSequence`**, bumper rules, competitive separation, etc.) the way IMA does. For pod-like behavior, rely on how your **ad server structures VMAP breaks**, or extend the engine if you need full in-VAST pod playback.

## Add dependency (JitPack)

### 1) Add the JitPack repository

#### Gradle (Kotlin DSL) `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

#### Gradle (Groovy) `settings.gradle`

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2) Add SDK dependencies

Replace `GITHUB_USER` and `VERSION` (a git tag such as `0.1.0` or a commit hash). JitPack artifact form: `com.github.GITHUB_USER:AdSDK:VERSION`.

#### Media3 integration (recommended)

```kotlin
dependencies {
    implementation("com.github.GITHUB_USER:AdSDK:VERSION")
}
```

When consuming this repository as composite builds or multiple artifacts, typical library modules are:

- **`:player-media3`** — primary API surface
- **`:core`**, **`:parser`**, **`:scheduler`**, **`:tracking`**, **`:network`**, **`:player-common`**
- **`:ui-compose`** — optional, for Compose-only ad chrome

## Usage with Media3 (IMA-like)

### What you provide

- A **content** **`ExoPlayer`** (Media3) and **`PlayerView`**
- An **`AdDisplayContainerView`** in the same parent as the player (typically full-bleed on top)
- A **`NetworkLayer`** implementation (OkHttp, Ktor, etc.)

### What the SDK provides

- **`Media3AdsLoader`**: **`requestAds(adTagUri)`** (fetch and classify VMAP/VAST), **`start()`**, optional seek-bar midroll markers via **`setAdMarkersContainerView`**
- Ad video rendering and **built-in ad UI** (skip, countdown) inside the container; styling via **`AdSdkUiConfig`** and resources (**`values/`**, **`values-fa/`**, etc.)

### XML layout example

```xml
<FrameLayout
    android:id="@+id/player_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.media3.ui.PlayerView
        android:id="@+id/contentPlayerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <tech.done.ads.player.media3.ima.AdDisplayContainerView
        android:id="@+id/adDisplayContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
```

### Kotlin example (Activity)

```kotlin
class PlayerActivity : AppCompatActivity() {

    private val scope = MainScope()

    private lateinit var contentPlayer: ExoPlayer
    private lateinit var adsLoader: Media3AdsLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val contentPlayerView = findViewById<PlayerView>(R.id.contentPlayerView)
        val adDisplayContainer = findViewById<AdDisplayContainerView>(R.id.adDisplayContainer)

        contentPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri("https://example.com/content.mp4"))
            prepare()
            playWhenReady = true
        }
        contentPlayerView.player = contentPlayer

        val network: NetworkLayer = /* your OkHttp/Ktor implementation */

        adsLoader = Media3AdsLoader(
            network = network,
            scope = scope,
        ).apply {
            setPlayer(contentPlayer)
            setAdDisplayContainer(adDisplayContainer)
            setAdMarkersContainerView(contentPlayerView)
        }

        adsLoader.isAdPlaying
            .onEach { adPlaying ->
                // Hide content controls while ads are active, etc.
            }
            .launchIn(scope)

        adsLoader.requestAds("https://your-ad-server.example/vmap-or-vast")
        adsLoader.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        adsLoader.release()
        contentPlayer.release()
        scope.cancel()
    }
}
```

### Compose example (AndroidView)

```kotlin
@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val contentPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    val network: NetworkLayer = remember { /* ... */ }
    val adsLoader = remember { Media3AdsLoader(network = network, scope = MainScope()) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = contentPlayer
                    adsLoader.setPlayer(contentPlayer)
                    adsLoader.setAdMarkersContainerView(this)
                }
            }
        )

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                AdDisplayContainerView(ctx).also { adsLoader.setAdDisplayContainer(it) }
            }
        )
    }

    LaunchedEffect(Unit) {
        adsLoader.requestAds("https://your-ad-server.example/vmap-or-vast")
        adsLoader.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            adsLoader.release()
            contentPlayer.release()
        }
    }
}
```

### Pre-fetched VMAP XML

If you already have VMAP XML (CDN, cache, assets):

```kotlin
adsLoader.requestAdsFromVMAPXml(vmapXmlString)
adsLoader.start()
```

## Usage with ExoPlayer2

The **IMA-like container and loader** are implemented for **Media3**. For **ExoPlayer2**, this repository provides **`ExoPlayer2Adapter`** for use with **`DefaultAdEngine`**; you supply ad UI yourself (or integrate **`ui-compose`** separately).

```kotlin
val contentPlayer: com.google.android.exoplayer2.ExoPlayer = /* ... */
val adapter = ExoPlayer2Adapter(player = contentPlayer, scope = scope)

val engine = DefaultAdEngine(
    player = adapter,
    vmapParser = VMAPPullParser(),
    vastParser = VASTPullParser(),
    scheduler = VMAPScheduler(),
    network = network,
    tracking = tracking,
    mainDispatcher = Dispatchers.Main,
).apply { initialize() }

lifecycleScope.launch { engine.loadVMAP(vmapXmlString) }
engine.start()
```

## Notes

- **VMAP vs VAST:** **`requestAds`** inspects the first XML root tag; unsupported roots fail fast with a clear error.
- **Debug logging:** **`Media3AdsLoader(..., debugLogging = true)`** or **`AdSdkLogConfig.isDebugLoggingEnabled`** controls verbose tracing where implemented.
- **Threading:** Configure **`Media3AdsLoader`** (player + ad container) on the **main** thread.
