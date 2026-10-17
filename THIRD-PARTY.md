# Third-party software

Papan for Android is GPL-3.0-or-later. Its application source, resources, test source, build configuration, dependency locks/verification metadata and build script ship in every APK under `assets/papan-source/`, with the GPL license.

| Component | License | Use and source |
| --- | --- | --- |
| Material Components 1.13.0 | Apache-2.0 | Native Material 3 UI. [Exact source](https://github.com/material-components/material-components-android/tree/1.13.0). Its source JAR ships under `assets/third-party/`. |
| Material Design icons | Apache-2.0 | Google's outlined phone, desktop, history, search, tune, add, close and play-arrow vectors, adapted from the [official sources](https://github.com/google/material-design-icons/tree/master/src) to Android vector resources in `android/res/drawable/`. |
| AndroidX libraries | Apache-2.0 | AppCompat, activity, core, and Material's transitive Android support libraries. Exact versions and artifact checksums are in `android/gradle.lockfile` and `android/gradle/verification-metadata.xml`. [AndroidX source](https://android.googlesource.com/platform/frameworks/support/). Bundled library notices are retained by Android packaging. |
| Kotlin standard library | Apache-2.0 | Material/AndroidX transitive runtime. Exact version/checksums are pinned with the other dependencies. [Source](https://github.com/JetBrains/kotlin). |
| ZXing core 3.5.4 | Apache-2.0 | On-device QR decoding. [Exact source](https://github.com/zxing/zxing/tree/zxing-3.5.4). Its source JAR ships under `assets/third-party/`. |
| youtubedl-android 0.18.1 | GPL-3.0 | Android Python 3.12 and FFmpeg runtime, initialized through the pinned native wrapper. [Wrapper and native build recipes](https://github.com/yausername/youtubedl-android/tree/0.18.1). Library and FFmpeg wrapper source JARs ship under `assets/third-party/`. Native executable libraries are installed through Android packaging. No in-app executable update is enabled. |
| Desktop Papan media worker | GPL-2.0-only | The exact desktop worker runs as a separate Python process. Its origin and SHA-256 are recorded in `android/media-runtime.lock.json`; source ships under `assets/papan-source/android/python/`. |
| gallery-dl 1.32.14, yt-dlp 2026.8.19 and Instaloader 4.15.3 | GPL-2.0-only / Unlicense / MIT respectively | Desktop's gallery/video extractors and their Python dependencies run on the phone. Exact upstream source URLs, licenses and SHA-256 values are pinned in `android/media-runtime.lock.json`; every corresponding source archive, including its notices, ships in `assets/media-runtime/`. The Python packages are imported from a deterministic ZIP. Browser-session access is not included. |
| Jackson and Commons IO | Apache-2.0 | Native-wrapper dependencies; exact versions/checksums are pinned in the Gradle lock and verification files. |
| Android platform APIs | Apache-2.0 platform source | Camera2, app-private storage, media playback and scheduled delivery. These APIs are provided by Android. [Platform source](https://source.android.com/). |
| Gradle wrapper 8.13 and Android Gradle Plugin 8.9.3 | Apache-2.0 | Build tools, not app runtime. [Gradle source](https://github.com/gradle/gradle/tree/v8.13.0), [Android build tools](https://android.googlesource.com/platform/tools/base/). Gradle distribution and dependency bytes are checksum-pinned. |

The Apache-2.0 license ships as `assets/Apache-2.0.txt`. Gradle verifies runtime artifacts and source archives against committed SHA-256 metadata. Signing keys, phone queues, desktop credentials and user data are excluded from source and release assets. Preserve the corresponding source and license notices when redistributing.
