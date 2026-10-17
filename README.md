# Papan for Android

Keep reference collections on your phone, or browse and add to open collections in [Papan desktop](https://github.com/chelij/papan). The Android app combines soft notebook surfaces with standard Material 3 controls, desktop Papan's green accent and its aligned media board. Single-line titles and source websites sit inside each preview, using the desktop board's smooth fade and regular-weight labels. Tap a pin for full-screen viewing, swipe between pins, zoom images and play videos in place. Preview sizing is adjustable, and light/dark appearance follows your phone unless you choose otherwise. Phone collections work without pairing. Choose collections from the dropdown beside the Papan wordmark. One bottom-left button switches between Phone, Desktop and Activity on the same row as Add link. Pin rows and columns meet without gaps; Board appearance is in the app-bar menu. Choose a nearby desktop on the phone and approve its matching code on the computer; QR pairing remains available. No cloud account is needed.

The location button highlights the current window in green and lists only the other two locations when opened. It stays tappable, and its size stays steady when switching. Picker labels share the same scalable type size. Collection dropdowns highlight the selected row; neither dropdown uses selection checkboxes.

Install [Android 0.3.1](https://github.com/chelij/papan-android/releases/tag/v0.3.1) for these mobile-board features and capture/storage reliability fixes. [Changelog and previous-release diff](CHANGELOG.md). Desktop browsing, nearby approval, session access and preview metadata require [Papan desktop 0.3.3 or newer](https://github.com/chelij/papan/releases/tag/v0.3.3).

[Download the published APK](https://github.com/chelij/papan-android/releases/latest) · [Desktop downloads](https://github.com/chelij/papan/releases/latest) · [Local sharing protocol and troubleshooting](https://github.com/chelij/papan/blob/main/docs/mobile-sharing.md) · [Ecosystem and compatibility](https://github.com/chelij/papan/blob/main/docs/ecosystem.md)

## Use it

1. Install the APK. Android 8+ is supported. In **Phone**, open the toolbar menu and choose **New collection**. The same menu renames or deletes a phone collection.
2. Choose **Paste link** from the toolbar menu, tap **Add link**, or share a link from another app to **Papan**. Choose its collection in the destination sheet. Choosing a phone collection saves the link immediately, then fetches its preview. Open a pin to view media, edit its title/notes, save to another collection, open its source, retry a failed preview or delete it. Tap the search icon to filter the current collection.
3. For desktop access, connect both devices to a reachable trusted local network. In desktop **Receive from phone**, enable the receiver and **allow paired phones to browse and add to open collections**, choose the default destination/address, and save. The default TCP port is 47778. Browsing needs desktop 0.3.3+; desktop 0.2.1+ still supports legacy default delivery.
4. In Android **Connections**, tap **Pair desktop**, choose a nearby computer and approve the matching six-digit code in desktop Papan within two minutes. The desktop must have its receiver enabled. If discovery is unavailable, choose **Pair a phone** on desktop and **Scan QR** on the phone, allow camera access and scan the QR. **Paste pairing link instead** and **Pair from link** work without a camera.
5. Switch to **Desktop** to browse currently open collections. Protected collections ask for their password on this phone, even if desktop already unlocked them. The paste/share picker also lists desktop collections. The desktop extracts and saves remote links into the chosen collection.

Open **Board appearance** using the toolbar menu. Previews reach both screen edges and zoom and crop to fill their tiles by default, including desktop collections. Change **Preview size** to fit more or fewer aligned cards, or turn off **Fill image frames** to see whole images. Each collection remembers its size and frame fit on this phone; desktop collections still retain their desktop frame proportions. The same panel offers **Use phone setting**, **Light** and **Dark** appearance.

The top bar and bottom location/Add buttons hide together while scrolling down and return together when scrolling up, freeing more space for previews. They share one continuous movement, including when you reverse direction. Changing collections, opening an empty board or focusing navigation with a keyboard brings them back. Focused search and Android touch exploration keep navigation available; short boards keep it visible.

Desktop pins follow the order arranged on desktop, including manual moves picked up by the foreground board's five-second refresh. Each protected collection needs its password once per app session; switching between already unlocked collections keeps their access. Actual app close or desktop locking still ends access. Phone-only collections retain their newest-first order.

Desktop video cards show still-image covers generated from their saved previews. These thumbnails follow the same pairing and collection-password access as other remote media. Opening a video plays it in the fitted full-screen viewer, with Play/Pause and a seek slider. Vertical swipes move between pins; sideways swipes move between attachments. Details contains the full title, source and editing actions. Desktop preview frames follow the collection's desktop proportions, including vertical frames; phone-only size/fit overrides remain adjustable. Decoded thumbnails are reused in bounded memory while scrolling. The matching desktop update is required for video covers.

Phone capture saves ordinary HTML titles/Open Graph thumbnails and direct JPEG/PNG/WebP/GIF images or MP4/WebM videos up to 32 MiB. Previews are cached privately for offline use. It does not run desktop's site-specific extractors or page JavaScript; a failed preview keeps the link and offers retry. Phone and desktop libraries are separate, with no automatic synchronization or desktop `.papan` import/export in this version. Phone collections have no collection-password feature.

Undelivered desktop links remain in the phone's private queue, with their chosen collection ID. Reopen Papan or use **Activity → Send pending / check saves** to retry; Android may defer background retries. Completed saves leave the queue after desktop acknowledgment. Repeating a link for the same destination does not duplicate it; different collections can each keep the link. Offline or older desktops offer an explicitly labelled default-delivery choice. An unavailable selected collection never silently falls back to the default.

The phone automatically finds the same paired desktop after its local IP address or receiver port changes, then verifies its identity before updating the saved address. Desktop follows address changes on its selected network interface. Browsing and pending deliveries retry through this connection; Android may delay background work. Use Android 0.3.0+ with desktop 0.3.3+ and allow local discovery on the network. If discovery is blocked, **Connections → Update desktop address** retains the key, pending links and selected destinations; QR/manual pairing remains available for older desktops.

Android keeps one active desktop pairing. **Pair desktop** can reconnect the existing computer while links are pending. Pairing a different computer stays blocked until those links are sent or explicitly dismissed in **Activity**. Automatic reconnection does not synchronize the separate phone and desktop libraries. Rotating or resizing keeps the selected protected collection unlocked through normal Activity recreation. With the matching desktop update, switching away, rotating and changing the theme keep password access for the running app session. Closing Papan or Android ending its process requires the password again. Desktop lock/close, revocation, protection changes or restart still revoke access. Older desktop providers retain their 15-minute lifetime. Session identities and passwords are never saved; a new process cannot reuse an old grant. Remote previews remain in memory, with no disk cache. The phone checks desktop access while viewing, including open details.

Camera frames are decoded locally and never saved or uploaded. Clipboard access happens only when you tap Paste. Sharing uses ordinary HTTP on a trusted local network; the phone stores its key and queue in app-private files with Android backup disabled. [Detailed transport and collection-protection limits](https://github.com/chelij/papan/blob/main/docs/mobile-sharing.md#privacy-and-protection).

## Build

Install Node.js 24.15+, JDK 17+, and Android SDK **platform 35 / build-tools 35.0.0**. Set `JAVA_HOME` and `ANDROID_HOME`, then:

```sh
git clone https://github.com/chelij/papan-android.git
cd papan-android
npm run check
npm run test:links
npm run test:capture
npm run build:android
```

`test:links` compiles and runs the production shared-link parser using only the JDK. It checks exact URL preservation and invalid-input rejection without the Android SDK, a device, or an APK installation. Directly pasted or shared URLs retain punctuation in their paths and query values; links extracted from surrounding prose retain the existing sentence-punctuation cleanup.

`test:capture` uses the JDK and Android SDK platform 35 to run the real capture HTTP fetcher against a disposable local server. It checks media-type parameters, image/video extensions, exact response bytes, and XHTML responses at and above the 1 MiB page limit, with both fixed-length and chunked transfers. It does not install an APK or exercise Android UI or storage.

No npm runtime dependencies or system Gradle installation are needed. The wrapper downloads checksum-pinned Gradle 8.13. Android Gradle Plugin 8.9.3, Material Components 1.13.0, AndroidX and ZXing core 3.5.4 are pinned in Gradle dependency locks and checked against committed SHA-256 verification metadata. The signed APK and SHA-256 file appear in `dist/`; source and dependency licenses are also embedded in the APK.

The first local build creates `artifacts/android/papan-local.keystore`. Keep it private and preserve it for updates. Another build key can be supplied using `PAPAN_ANDROID_KEYSTORE`, `PAPAN_ANDROID_KEY_ALIAS`, and `PAPAN_ANDROID_KEY_PASSWORD`. Official releases use the existing maintainer key; a locally generated key cannot update an installation signed by another key. Neither signing keys nor passwords are uploaded to this repository. CI compiles and verifies a disposable-key APK; only maintainer-signed release assets are distributed.

## Device checks

Build the isolated test package:

```sh
npm run build:android -- --test
```

For native storage regressions, choose a booted disposable headless emulator and run:

```sh
PAPAN_ANDROID_SERIAL=emulator-5556 npm run test:storage
```

This command installs and resets only `com.papan.share.test` on the selected emulator. It rejects physical devices and checks preview replacement, retained caches, failed-write recovery, storage reopening, shared-file ownership and final cleanup without opening an Activity. Do not use an emulator that another task owns.

Then, from a [Papan desktop checkout](https://github.com/chelij/papan) with its development dependencies installed:

```sh
PAPAN_ANDROID_SERIAL=emulator-5554 PAPAN_ANDROID_DIR=/path/to/papan-android PAPAN_ADB=/path/to/adb npm run test:android-ui
```

To include native design/sizing/theme checks with the existing credited desktop artwork cache, also set `PAPAN_ANDROID_DEMO_ROOT=/path/to/papan`. This opt-in test fixture is copied only into the disposable test package; no sample library or artwork is bundled with the app.

Use a disposable headless emulator and set `PAPAN_ANDROID_SERIAL` to its emulator serial. The test rejects physical-phone targets before running ADB. It installs only `com.papan.share.test`, verifies its private virtual display before operating the UI, and uses a disposable desktop collection and temporary emulator reverse tunnels. It checks native Material navigation, clipboard paste/deduplication, standalone collections and cached media, live Camera2 frames, QR decoding at four rotations, pairing, selected-destination saves and saved-status updates. Desktop pagination/search, protected access, offline restart/retry, video playback/deletion and narrow/tablet/200% text layouts are covered by the same run. It preserves a readable clipboard value and removes its test app/tunnels afterward. `PAPAN_ANDROID_CAMERA_ONLY=1` skips clipboard checks.

For a focused location-menu check, run this from the Android checkout after building the test APK:

```sh
PAPAN_ANDROID_SERIAL=emulator-5554 PAPAN_ADB=/path/to/adb npm run test:location-menu
```

This captures actual virtual-display animation frames and checks that the location menu reveals upward without covering its button. It also checks location choices, consistent dimensions/typography and dismissal at narrow, normal, tablet and enlarged-text sizes. Results are under `artifacts/android/location-menu/`; no phone or desktop receiver is needed.

`npm run test:scroll-controls`, with the same emulator variables, verifies real swipes, synchronized top/bottom progress throughout native frames and compositor pixels, reclaimed header height, refresh position retention, direction reversal, keyboard recovery and empty/short boards across the same responsive sizes. Native screenshots, motion measurements and results are under `artifacts/android/scroll-controls/`. The full desktop-backed suite also checks shared motion on remote pin previews.

The unreleased 0.3.0 development pair was checked on 14 October 2026 using an isolated Android 16/API 36 x86_64 emulator and virtual display, against desktop source based on `beec3da`. Native checks cover local collection creation, cached images and actual video playback/deletion, pin edit/search, durable private storage, destination selection while desktop is offline, restart/retry without destination changes, protected cancel/wrong/correct passwords, an actual protected save and clearing contents after desktop lock. An 85-pin collection checks pagination, search beyond loaded pages, card recycling and position retention. Narrow phone, tablet, 200% font and keyboard layouts are also checked. Build tools are 35.0.0/JDK 21. See [verification](docs/verification.md) for exact evidence and limits.

Earlier Android 0.2.0 camera/QR/delivery checks passed on Samsung SM-S916B / Android 16. Live camera frames and rotated QR fixtures are separate from optical scanning. The current emulator checks do not establish physical-phone performance, optical scanning, Wi-Fi reachability, Android 8 behavior or a published desktop/APK pair. iPhone preparation remains in the desktop protocol guide; this repository builds Android only.

## License

[GPL-3.0-or-later](LICENSE). [Dependency notices](THIRD-PARTY.md).
