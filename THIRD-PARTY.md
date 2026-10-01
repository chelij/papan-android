# Third-party software

Papan's Android companion is GPL-3.0-or-later. Its source and build script, along with the GPL license, are included in every release APK under `assets/papan-source/`.

| Component | License | Use and source |
| --- | --- | --- |
| ZXing core 3.5.4 | Apache-2.0 | On-device QR decoding. [Exact source](https://github.com/zxing/zxing/tree/zxing-3.5.4). The build verifies pinned SHA-256 checksums for its binary, source JAR, and license. The source JAR and Apache license ship in APK assets. |
| Android platform APIs | Apache-2.0 platform source | Native UI, Camera2, app-private storage, and scheduled delivery. These APIs are provided by Android; no Android support library is bundled. [Platform source](https://source.android.com/). |

The signing key, local phone queue, desktop credentials, and user data are never included in source or release assets. Keep corresponding source and license notices when redistributing the companion.
