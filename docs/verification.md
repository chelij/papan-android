# Mobile boards verification

Checked **14 October 2026**. Android **0.3.0 / version code 4**, based on `28025ab`, with the desktop mobile-board patch based on **`beec3da` / 0.3.2**. These are unreleased preview builds. Personal applications, collections and signing keys were not replaced.

## Native and integration checks

The runtime was Android **16 / API 36 x86_64**, in a disposable, headless emulator. Instrumentation installed only `com.papan.share.test` and verified an independent virtual display before each interaction/capture, including fresh displays for configuration checks. The ordinary `com.papan.share` APK was built and signature-verified. Both variants compile the same application source and resources; instrumentation and test manifest changes are isolated to the test variant.

The build used Node **26.11.1**, JDK **21**, SDK platform/build-tools **35 / 35.0.0**, Gradle **8.13**, Android Gradle Plugin **8.9.3**, Material Components **1.13.0** and locked AndroidX dependencies. APKs verify with signatures v2 and v3. Source, dependency verification metadata and license notices are embedded in the APK.

| Check | Result and direct evidence |
| --- | --- |
| Fresh phone library | Native collection creation, rename, deletion/cancellation, title/notes editing, search, private-state reopen and per-collection deduplication pass. Local saves never enter the desktop queue. |
| Capture and recovery | Actual image decoding, direct MP4 capture and native playback past 200 ms pass. Deletion removes its private media. Failed HTTP capture retains the link and retry remains usable. |
| Paste/share destination | Clipboard paste, add-link validation, Android share intent, destination-sheet cancellation and saving to another collection pass. Offline delivery preserves its collection ID and request ID across storage/activity restart. |
| Desktop access | Open collections load from the actual receiver. An 85-pin board checks newest-first pages of 40/80, refresh retaining 80 and its position, recycling fewer than 40 attached cards, and server search finding an unloaded pin. |
| Password and lock | Cancel reveals no protected content; a wrong password is rejected; the correct password saves a third image into the protected collection and opens its board. Desktop lock closes details and clears remote pins. The fixture is still locked afterward; an explicit fixture-only unlock confirms both protected pins persist. |
| Pairing and receipt | Live Camera2 frames, invalid-pairing recovery, four rotated QR fixtures, pairing to the actual desktop receiver and three completed saves pass. Camera frames and QR-fixture decoding are separate checks. |
| Material UI | Bottom navigation at phone widths, navigation rail at 640dp, one/three-column reflow, 200% fonts, 48dp navigation/FAB targets, native keyboard Tab/Enter and Android Back pass. Source browser intent and address recovery controls pass. |
| Desktop provider | All **65** desktop unit groups pass, including real HTTP authorization/ranges, per-device password grants, bounded pages, selected destinations, restart/revocation and encrypted stream cancellation after garbage collection. Native source and packaged desktop phone suites pass on private X display `:97`; desktop settings checks pass. |

Run from Android:

```sh
npm run check
npm run build:android
npm run build:android -- --test
```

Then run the paired desktop checkout's `test:android-ui` as described in the [README](../README.md#device-checks). Desktop evidence is in `artifacts/android/ui/report.json`, `instrumentation.txt`, the local/remote/narrow/tablet/large-text PNGs, `artifacts/phone-check.json`, `artifacts/phone-package-check.json` and `artifacts/mobile/`. Android build logs and computed color ratios are in `artifacts/android/`. These ignored local artifacts can be regenerated; they are not public release evidence.

## Exact preview bytes

| Artifact/source | SHA-256 |
| --- | --- |
| `Papan-Android-0.3.0.apk` | `b30e84fb092813da5b8ffc48f5cecb3b7ed88d1873e0d331936a9db6a3214260` |
| `Papan-Android-0.3.0-test.apk` | `f729f1189677dd32c899d22a288c40f9e3f884be7fbcefb50382fe301cfd1ff8` |
| `Papan-mobile-preview-linux-x64.tar.gz` | `558e2ae6a695a8bbe9e04d63b98081767be6d61e9b4fb3f7f6cc65fe705c6570` |
| `MainActivity.java` | `cde21d7754dd13df613bd5516c02b29348ff379d2bf4950d5eb051c13d347e7e` |
| `MobileLibrary.java` | `e9a47b7c5266a1d5f59f01aa2b8e6ef04d9ff8f66cb49700708ad758ebdcffd8` |
| `DesktopLibrary.java` | `196569273131e1db146a666c0abc4506d1cf8b36d88d6eea6bf99b34caabb6bb` |
| `LocalCapture.java` | `1bb2452d9d060974accbd16243a1d9763d34d4f92c3be883ec46651abbd329ba` |
| Desktop `src/phone-library.js` | `0fe6030dfbaf2ac3693859f34a7c5e09de2d10e126479fe2903625fa16faf811` |

The desktop package's provider bytes match the checked source. APK checksums refer to the local signed artifacts; rebuilding may produce a different archive hash. [Design and accessibility gate](mobile-design-checks.md), [coordinator](https://github.com/chelij/papan/issues/5), [Android task](https://github.com/chelij/papan-android/issues/2).

## Notebook style and aligned sizing

The subsequent 14 October 2026 revision, based on Android `5cd0c87`, implements the user's mymind reference with aligned rows/columns and adjustable preview sizing. It keeps Material Components 1.13.0 and all existing storage, capture, delivery and receiver contracts. The checked desktop provider remains `6de1daa` with `src/phone-library.js` SHA-256 `0fe6030dfbaf2ac3693859f34a7c5e09de2d10e126479fe2903625fa16faf811`; only its Android test harness/documentation change for this revision.

Normal and isolated Android 0.3.0/version-code-4 APKs build and verify v2/v3 signatures with JDK 21, SDK/build-tools 35/35.0.0 and the existing signing key. All **23** common compiled application classes are byte-identical between variants. All **18** current application source/resource files match the normal APK's source bundle. Signing keys and seeded artwork/library data are absent. These preview bytes supersede the earlier black/green preview at the same unreleased version.

The final native run passes on Android 16/API 36 x86_64, using verified virtual display **26** inside a headless, read-only emulator. The existing collection/capture/video/paste/share/pairing/receipt/offline/search/pagination/password/lock flows repeat successfully, including three actual receiver saves. New checks use eight real, already-cached public-domain artworks from the desktop [credited catalog](https://github.com/chelij/papan/blob/main/docs/demo/credits.md), with varied image proportions and long titles. Native card bounds have equal heights/widths and aligned row/column edges at small, medium and large preview settings (three/two/one columns at 360dp). Real slider keyboard input changes the grid, a non-default size survives fresh Activity initialization, another collection retains its own size, and rapid size changes retain the visible reference. Fill/whole-image settings update actual ImageViews. Light/Dark/System controls work; a fresh dark pairing screen inherits the selected theme. Toolbar icon tints stay readable after switching back to light mode.

The unchanged responsive checks pass at 300dp, 640dp and 200% fonts, with native navigation/FAB bounds, 48dp touch targets and keyboard activation/dismissal. All **16** measured custom text/control color pairs meet their AA thresholds in both themes: text ratios **5.56–14.83:1**, control outline ratios **4.28/5.20:1**. A physical screen-reader/soft-keyboard session remains untested. Visual inspection of the final native light, dark, size-panel, compact and enlarged captures complements the geometry checks; the user's aesthetic judgment remains separate from this implementation evidence.

| Updated preview/source | SHA-256 |
| --- | --- |
| `Papan-Android-notebook-preview.apk` / normal `Papan-Android-0.3.0.apk` | `6a43be13f7bfaf0ffd9f67af2322c537b37171b75c3fd0aa4285a79847de4f1c` |
| `Papan-Android-0.3.0-test.apk` | `df516a4893ed4800339cba8d903d7f2e247321cb505c3f7dde9a79e924131ced` |
| `MainActivity.java` | `ecf0ef040c6463fd29f1f57572e8618abfd9e129aa132c2aedb535a6bd587c56` |

Reproduce the native run from the matching desktop checkout with `PAPAN_ANDROID_DEMO_ROOT=/path/to/papan` in addition to the README's Android directory, ADB and serial settings. Evidence there is `artifacts/android/ui/report.json`, `instrumentation.txt`, `style-light.png`, `style-dark.png`, `style-small.png`, `style-large.png` and `style-appearance.png`. Android build/signature logs, measured colors and source/class integrity are in `artifacts/android/redesign-*`. Both source checks pass. Previously recorded 65-unit/source/package desktop-provider evidence is reused because the provider bytes are unchanged.

No personal installation or published release is claimed. Physical-phone/Wi-Fi/optical QR, Android 8 and screen-reader/physical soft-keyboard gaps remain. Native tests use temporary receiver collections and ADB reverse tunnels. No host Xvfb server or primary-monitor test window is used in this revision. [Design gate](mobile-design-checks.md), [Android task #2](https://github.com/chelij/papan-android/issues/2), [coordinator #5](https://github.com/chelij/papan/issues/5).

## Limits

No physical-phone performance, optical QR scan, Wi-Fi reachability, Android 8 run, iOS app, published release pair or personal installation is claimed. The emulator uses ADB reverse tunnels to temporary loopback services. The source-browser check intercepts the intent; it does not operate another browser. Keyboard focus/activation and responsive bounds were checked; a screen-reader session and physical soft-keyboard interaction remain device follow-up checks.

Phone capture supports ordinary HTML metadata and bounded direct images/video, with static image previews. It does not run desktop's site-specific extractors or JavaScript. Phone and desktop collections remain separate; local collection encryption, `.papan` interchange and implicit synchronization are outside this version. Desktop browsing and explicit destination selection require the matching provider patch. Legacy default delivery keeps its existing behavior. HTTP transport is restricted to a trusted local network and is disclosed in the connection/password UI.
