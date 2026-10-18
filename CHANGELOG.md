# Changelog

## Unreleased

## 0.3.2 - 2026-10-18

Changes since [v0.3.1](https://github.com/chelij/papan-android/releases/tag/v0.3.1): [full comparison](https://github.com/chelij/papan-android/compare/v0.3.1...v0.3.2) · [download the diff](https://github.com/chelij/papan-android/releases/download/v0.3.2/papan-android-v0.3.1-to-v0.3.2.diff).

### Changed

- Paste or share a link, choose its images/videos, choose a Phone collection, then download the selection for offline viewing. The phone uses the pinned desktop gallery-dl, yt-dlp and Instaloader workers through Android Python/FFmpeg. Multiple attachments retain their order and chosen identities; each file is capped at 32 MiB.
- Use portrait thumbnails and collection frame proportions for vertical Phone videos. Visible Phone and authorized Desktop video previews autoplay muted and loop; Desktop uses saved preview streams when available. Offscreen, viewer, background and lock transitions release playback.
- Pause previews at their current frame before location, collection and toolbar menus open; resume the same player on dismissal.
- Move a Phone pin through **Edit pin → Collection**. Save its destination, title and notes together while retaining its identity and downloaded media. Close cancels the edit.
- Keep location labels on one line, size the button for the widest label and let the native menu scroll when height is limited. Pin actions share the three-dot menu, with swipe navigation and keyboard/accessibility alternatives.
- Prefer downloadable webpage video over its poster. Failed retries and failed commits preserve existing media and selected identities; cleanup follows successful state commits.

### Compatibility and checks

- Version code increases to 6. APKs retain the maintainer certificate for data-preserving updates. Phone library/queue and receiver protocols remain v1; optional selected-media fields retain legacy cover compatibility. Desktop browsing requires Papan 0.3.3+ and existing browse/add consent. Desktop editing/removal and pose extraction remain desktop-only.
- Public supported media may still be unavailable; private/sign-in/DRM sources are outside this flow. Phone and desktop libraries remain separate.
- API 36 headless-emulator evidence covers actual downloaded files/playback, authenticated streams, menu frame retention, collection moves/rollback, order/password sessions, responsive themes and three receiver saves. Release packaging reuses unchanged runtime evidence and verifies signatures, embedded sources and a native package upgrade. [Release verification](docs/verification.md#v032-release).

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
