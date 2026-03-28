# AdSDK (IMA-like Ads SDK for Android)

An Android Ads SDK with **VMAP/VAST parsing**, **ad scheduling**, **tracking**, and an **IMA-like integration** for **Media3**:

- Your app **owns the content player**
- The SDK renders ads + ad UI inside an `AdDisplayContainerView`
- You call `requestAds(adTagUri)` like Google IMA (SDK auto-detects VMAP vs VAST)

## Modules (current)

- **`core`**: ad engine (`DefaultAdEngine`)
- **`parser`**: VMAP/VAST pull parsers + models
- **`scheduler`**: VMAP → ad timeline scheduling
- **`tracking`**: tracking engine
- **`network`**: `NetworkLayer` abstraction
- **`player-common`**: player-agnostic interfaces/models (`PlayerAdapter`, `PlayerState`, listeners)
- **`player-media3`**: **IMA-like** Media3 integration (`Media3AdsLoader`, `AdDisplayContainerView`)
- **`player-exoplayer2`**: ExoPlayer2 adapter (`ExoPlayer2Adapter`) for low-level integration
- **`sample-app`**: sample app showing Media3 usage

## Add dependency (JitPack)

### 1) Add JitPack repository

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

### 2) Add the SDK dependencies

> Replace `GITHUB_USER` and `VERSION`.
> - `VERSION` should be a git tag like `0.1.0` (recommended) or a commit hash.
> - Artifact format on JitPack: `com.github.GITHUB_USER:AdSDK:VERSION`

#### Media3 integration (recommended, IMA-like)

```kotlin
dependencies {
    implementation("com.github.GITHUB_USER:AdSDK:VERSION")
    // If you prefer splitting artifacts later, we can publish per-module.
}
```

If you want to depend explicitly on modules from this repo (when published as separate artifacts later), these are the modules used in the sample:

- `:player-media3` (IMA-like API)
- `:core`, `:parser`, `:scheduler`, `:tracking`, `:network`, `:player-common`

## Usage with Media3 (IMA-like)

### What you provide

- A **content** `ExoPlayer` (Media3) + `PlayerView`
- An **`AdDisplayContainerView`** on top of your player (in the same parent)
- A `NetworkLayer` implementation (OkHttp/Ktor/etc)

### What the SDK provides

- `Media3AdsLoader`
  - `requestAds(adTagUri)` (fetch + detect VMAP/VAST)
  - `start()` (start ad scheduling / preroll)
- Ad video rendering + **built-in ad UI overlay** (Skip/Countdown) inside the container

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
            // Optional: if you want yellow ad markers on the seekbar.
            setAdMarkersContainerView(contentPlayerView)
        }

        // Reliable signal for custom ad playback (useful for Compose/custom controllers).
        adsLoader.isAdPlaying
            .onEach { adPlaying ->
                // Hide content controls/overlays while ads are showing.
                // controllerVisible = !adPlaying
            }
            .launchIn(scope)

        // IMA-like: only an adTagUri.
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
                    // Optional: if you want yellow ad markers on the seekbar.
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

## Usage with ExoPlayer2

Right now, the **IMA-like “container view + ads loader”** is implemented for **Media3**.

For **ExoPlayer2**, this repo currently provides a lower-level adapter (`ExoPlayer2Adapter`) that you can plug into `DefaultAdEngine`.
You manage the ad UI yourself (or we can add an ExoPlayer2 `AdsLoader`/container similar to Media3 next).

### Kotlin example (low-level)

```kotlin
val contentPlayer: com.google.android.exoplayer2.ExoPlayer = /* ... */
val adapter = ExoPlayer2Adapter(player = contentPlayer, scope = scope)

val engine = DefaultAdEngine(
    player = adapter,
    vmapParser = VmapPullParser(),
    vastParser = VastPullParser(),
    scheduler = VmapScheduler(),
    network = network,
    tracking = tracking,
    mainDispatcher = Dispatchers.Main,
).apply { initialize() }

// Load VMAP XML (string) then start.
engine.loadVmap(vmapXmlString)
engine.start()
```

## Notes

- **VMAP vs VAST detection**: `Media3AdsLoader.requestAds(adTagUri)` downloads the XML and checks the root tag (`VMAP`/`VAST`), then plays accordingly.
- **Ad UI**: the SDK’s overlay (Skip/Countdown) is internal to the library and localized via resources (`values/` + `values-fa/`).

