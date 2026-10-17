Papan for Android 0.3.1 improves link capture and protects cached previews during storage failures.

Changes since **v0.3.0**:

- Preserve punctuation in directly shared or pasted URLs.
- Accept media types with whitespace before MIME parameters.
- Bound XHTML capture to the existing 1 MiB page limit, including chunked responses.
- Delete replaced cache files only after the library commit succeeds and their final reference is removed.
- Stage recaptures separately, preserving the current preview when a commit fails and recovering cleanly on retry. Repeated uncommitted attempts reuse their candidate filename.

[Changelog](https://github.com/chelij/papan-android/blob/v0.3.1/CHANGELOG.md) · [Full comparison](https://github.com/chelij/papan-android/compare/v0.3.0...v0.3.1) · [Download the diff](https://github.com/chelij/papan-android/releases/download/v0.3.1/papan-android-v0.3.0-to-v0.3.1.diff)

Install `Papan-Android-0.3.1.apk` and verify its `.sha256` file. Version code is **5**. The APK uses the existing maintainer certificate, SHA-256 `427cd50690d9a1f00bf0df21e4305d81ec347400d42fba3d1fde7f7e61ba1bec`, for in-place updates preserving app data. The private key is excluded from source and assets.

The release requires 13 URL tests, nine real-HTTP capture tests, eight native storage scenarios, signed normal/test variants, embedded-source verification and the desktop-backed workflow on a disposable Android 16/API 36 headless emulator. Public package bytes, certificate and source are verified after publication. Physical phone access is limited to rollback preparation, installation and package verification, with no app launch or UI test. [Exact results and publication evidence](https://github.com/chelij/papan-android/blob/main/docs/verification.md#v031-release).

Phone-library and queue state and receiver protocol remain v1. Desktop browsing, session access, preview metadata and nearby recovery require desktop 0.3.3+; [desktop 0.3.4](https://github.com/chelij/papan/releases/tag/v0.3.4) carries companion capture fixes. Phone and desktop libraries remain separate. Capture supports bounded direct media and HTML metadata, without desktop site-specific extraction or JavaScript.

Android 8+ remains the declared minimum; behavior checks cover Android 16. Physical performance, optical QR, Android 8/iOS, screen-reader interaction and a complete published Wi-Fi release-pair check remain unverified. Sharing uses HTTP on a trusted local network. [Setup and protection limits](https://github.com/chelij/papan/blob/main/docs/mobile-sharing.md).

Licensed GPL-3.0-or-later. Source and dependency notices are embedded in the APK. [Dependency notices](https://github.com/chelij/papan-android/blob/v0.3.1/THIRD-PARTY.md).
