# Papan for Android

Share or paste a link on your phone and save it into a collection in [Papan desktop](https://github.com/chelij/papan). Pair locally with a QR code; the desktop extracts and saves the media. No cloud account or app-store credentials are needed.

[Download the APK](https://github.com/chelij/papan-android/releases/latest) · [Desktop downloads](https://github.com/chelij/papan/releases/latest) · [Local sharing protocol and troubleshooting](https://github.com/chelij/papan/blob/main/docs/mobile-sharing.md)

## Use it

1. Install the APK from Releases. Android 8+ is supported. Use **Papan desktop 0.2.1 or newer**, and connect both devices to a reachable local network.
2. On the desktop, open **Receive from phone**, enable the receiver, select a destination collection, and save the settings. Allow its chosen TCP port through the desktop firewall; the default is 47778.
3. Choose **Pair a phone** on the desktop. In the Android companion, tap **Pair desktop**, allow camera access, and scan the desktop QR. It pairs automatically. **Paste pairing link instead** works without a camera.
4. Share a link from another app to **Papan**, or copy a link and tap **Paste link**. That single button reads the current clipboard and queues/delivers its first web URL immediately.

Undelivered links remain in the phone's private queue. Reopen Papan or tap **Send pending / check saves** to retry immediately; Android may defer scheduled background retries. Completed saves are removed from the phone queue after the desktop acknowledges them. Repeated taps do not duplicate a pending URL.

**Connection settings** contains pairing and desktop address recovery. Update the address there if the desktop's local IP changes; this keeps the key and pending links. Replacing a pairing is blocked while pending links exist.

Camera frames are decoded locally and never saved or uploaded. Clipboard access happens only when you tap Paste. Sharing uses ordinary HTTP on a trusted local network; the phone stores its key and queue in app-private files with Android backup disabled. [Detailed transport and collection-protection limits](https://github.com/chelij/papan/blob/main/docs/mobile-sharing.md#privacy-and-protection).

## Build

Install Node.js 24.15+, JDK 17+, and Android SDK **platform 35 / build-tools 35.0.0**. Set `JAVA_HOME` and `ANDROID_HOME`, then:

```sh
git clone https://github.com/chelij/papan-android.git
cd papan-android
npm run check
npm run build:android
```

No npm runtime dependencies or Gradle installation are needed. The script fetches checksum-pinned ZXing core 3.5.4. The signed APK and SHA-256 file appear in `dist/`; source and dependency licenses are also embedded in the APK.

The first local build creates `artifacts/android/papan-local.keystore`. Keep it private and preserve it for updates. Another build key can be supplied using `PAPAN_ANDROID_KEYSTORE`, `PAPAN_ANDROID_KEY_ALIAS`, and `PAPAN_ANDROID_KEY_PASSWORD`. Official releases use the existing maintainer key; a locally generated key cannot update an installation signed by another key. Neither signing keys nor passwords are uploaded to this repository. CI compiles and verifies a disposable-key APK; only maintainer-signed release assets are distributed.

## Device checks

Build the isolated test package:

```sh
npm run build:android -- --test
```

Then, from a [Papan desktop checkout](https://github.com/chelij/papan) with its development dependencies installed:

```sh
PAPAN_ANDROID_DIR=/path/to/papan-android PAPAN_ADB=/path/to/adb npm run test:android-ui
```

Unlock the USB-debugging phone first. Optional `PAPAN_ANDROID_SERIAL` selects a device. The check installs only `com.papan.share.test`, verifies its virtual display before operating the UI, and uses a disposable desktop collection and temporary USB reverse tunnel. It checks clipboard paste/deduplication, live Camera2 frames, QR decoding at four rotations, pairing, real media saving, and saved-status updates. It preserves a readable clipboard value and removes its test app/tunnel afterward. `PAPAN_ANDROID_CAMERA_ONLY=1` skips clipboard checks when the phone is locked.

These checks passed on a Samsung SM-S916B running Android 16. Live camera frames and QR fixtures were verified separately; optical scanning of a desktop screen and Wi-Fi reachability are not established by the USB test. iPhone preparation remains in the desktop protocol guide; this repository builds Android only.

## License

[GPL-3.0-or-later](LICENSE). [Dependency notices](THIRD-PARTY.md).
