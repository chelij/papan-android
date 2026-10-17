# Changelog

## 0.3.1 - 2026-10-17

Changes since [v0.3.0](https://github.com/chelij/papan-android/releases/tag/v0.3.0): [full comparison](https://github.com/chelij/papan-android/compare/v0.3.0...v0.3.1) · [download the diff](https://github.com/chelij/papan-android/releases/download/v0.3.1/papan-android-v0.3.0-to-v0.3.1.diff).

### Fixed

- Preserve punctuation in directly pasted/shared URL paths and query values. Links extracted from surrounding prose keep the existing sentence-punctuation cleanup.
- Accept whitespace before MIME parameters when capturing direct image/video responses.
- Apply the existing 1 MiB page limit to XHTML capture, including chunked responses.
- Remove replaced cached media only after a successful library commit and after its final owner releases it.
- Stage recaptured media under a separate deterministic filename. A failed library commit retains the current preview bytes, cleans the failed candidate and allows a successful retry; repeated uncommitted attempts reuse their candidate.

### Checks and compatibility

- Add SDK-free URL regressions, real HTTP capture regressions and native storage instrumentation. The checked runtime passes 13 URL, nine HTTP and eight native storage scenarios, signed normal/test builds and the Android 16/API 36 desktop-backed virtual workflow.
- Version code increases from 4 to 5. Official APKs use the existing maintainer certificate for in-place updates preserving app data. Phone library/queue state and receiver protocol remain v1; desktop browsing requires Papan 0.3.3+.
- Phone access is limited to installation, backup and package verification. Optical QR/Wi-Fi, physical performance, Android 8/iOS and screen-reader coverage retain their documented limits. [Release verification](docs/verification.md#v031-release), [component #6](https://github.com/chelij/papan-android/issues/6), [coordinator #10](https://github.com/chelij/papan/issues/10).

## 0.3.0 - 2026-10-16

Introduced standalone phone collections, aligned previews and full-screen media viewing, desktop collection browsing/saving, protected app-session access, nearby pairing and verified address recovery. [Original release notes](https://github.com/chelij/papan-android/releases/tag/v0.3.0).
