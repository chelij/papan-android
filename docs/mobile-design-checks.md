# Mobile design delivery gate

**PASS, 15 October 2026.** Direction: the user's reference app visual reference and photo-sharing service Explore interaction reference, adjustable aligned desktop-style frames, lighter single-line title/source captions, desktop green and standard Android Material behavior. ENERGY **1**, RHYTHM **1**, MOTION **1**. [Implementation direction](mobile-design.md) and [native evidence/limits](verification.md#explore-viewing-and-app-sessions-15-october-2026).

This report covers the changed mobile interface. Native Material components provide the platform interaction patterns; Papan supplies its identity and content. A physical screen-reader/soft-keyboard session is still a device follow-up, not a claimed result.

| Rule | Status and evidence |
| --- | --- |
| R-01 | PASS: desktop-style continuous fading separates media from readable captions; the design note records the phone contrast adjustment. No solid caption block or decorative gradient/glow. |
| R-02 | PASS: changed UI copy contains no em dashes. |
| R-03 | PASS: native 300dp/640dp/200% layouts reflow; navigation/FAB bounds and 48dp targets are asserted. |
| R-04 | PASS: official outlined Material phone, desktop, history, search, tune, add, close and play vectors identify real actions; the lock symbol denotes actual protection. New icon sources/licenses are recorded in THIRD-PARTY.md. |
| R-05 | PASS: app bar, tabs, media board, sheets and details serve distinct navigation/content levels. No marketing template. |
| R-06 | PASS: native scalable sans-serif follows Android guidance and desktop's sans-serif identity. |
| R-07 | PASS: no decorative background grid or pattern. |
| R-08 | PASS: no decorative arrow buttons. |
| R-09 | PASS: no promotional badges. The lock symbol denotes actual password protection. |
| R-10 | PASS: no glassmorphism. |
| R-11 | PASS: Material card corners, native dialogs/sheets and standard navigation/FAB shapes follow their component purposes. |
| R-12 | PASS: cards have zero elevation; standard floating actions and dialogs retain their platform hierarchy. |
| R-13 | PASS: no decorative glow layers. |
| R-14 | PASS: consistent reference cards support scanning a collection; the full-screen fitted viewer, separate Details sheet and forms follow their content and actions. |
| R-15 | PASS: actions name their result, including Paste link, Choose collection, Retry preview and Save changes. |
| R-16 | PASS: no AI marketing claims or buzzwords. |
| R-17 | PASS: no invented statistics; documented bounds and test counts come from source/test output. |
| R-18 | PASS: no testimonials or fictional people. |
| R-19 | PASS: standard Material interaction motion only; no decorative entrance/loop animations. |
| R-20 | PASS: the accepted notebook surfaces, desktop green action color, Papan wordmark, collection strip and aligned board form a consistent identity. |
| R-21 | PASS: working Use phone setting/Light/Dark controls persist the choice and recreate the native Activity. |
| R-22 | PASS: no generated illustrations. |
| R-23 | PASS: the user chose reference app styling and Explore-style viewing with Papan alignment/sizing. Existing Papan identity, licensed Material vectors and credited public-domain artwork fixtures are used; no identity imagery is generated. |
| R-24 | PASS: On phone, Desktop and Activity are implemented destinations. Collection tabs select real collections. |
| R-25 | PASS: all prior 18 measured text/control pairs meet AA. Actual fade pixels retain minimum title/source contrasts of 4.67/4.80:1 over white. The new white play indicator on a 60%-black scrim measures 5.74:1 in its worst case. Toolbar tints refresh with theme; native field/card focus remains visible. |
| R-26 | PASS: actual background/foreground and both rotations retain protected access; actual app close/reopen requires the password. Native decoded video pixels, local/remote playback, pause/seek, restored paused state/position, image zoom, pin swipes with pagination and attachment navigation pass. Existing collection/paste/share/edit/retry/delete/nearby/code-approval/QR/address-recovery/delivery flows use real HTTP and durable state, with three saves. |
| R-27 | PASS: Connections feedback scrolls inside its focused dialog at normal/200% fonts; board feedback waits for forms to close. Viewer boundary feedback appears inside the viewer. Posters/loading, playback error/Retry, offline, empty, locked and validation states are implemented. Protected thumbnails and measured proportions clear on desktop lock/close/revoke/unavailability. Verified endpoint recovery preserves keys/destinations and rejects wrong identities/stale manual-edit races. A corrupt-video fixture is not claimed. |
| R-28 | PASS: no generic FAQ. |
| R-29 | PASS: neutral notebook surfaces and one desktop green action accent define light/dark palettes; muted secondary navigation supports selection. |
| R-30 | PASS: reference app styling and Explore viewing are explicitly requested references; Papan retains its wordmark, aligned adjustable frames, local/desktop navigation and native Material controls. |
| R-31 | PASS: component purposes, spacing, typography, color and responsive behavior are documented in the design note. |
| R-32 | PASS: native Tab/Enter and Android Back pass; image zoom has keyboard activation/panning, video seeking accepts keyboard input, and pin/attachment navigation has button/menu alternatives to swipes. Cards and Material controls retain visible focus feedback. |
| R-33 | PASS: Java/XML directly implement the interface; the build compiles that source without UI rewriting scripts. |
| R-34 | PASS: Light/Dark/System controls, fresh theme initialization, restored selected collection/size, and readable toolbar icons pass native checks. |
| R-35 | PASS: signed final builds, fresh native initialization, actual video-surface pixels, inspected screenshots, viewer/grid/access/cache flows and real provider saves pass. Source/packaged desktop checks and Linux/Windows/macOS CI pass. Both apps are installed with matching bytes and preserved data/pairing. |
| R-36 | PASS: trusted-LAN HTTP and capture limits are disclosed. No certification or performance claims. |
| R-37 | PASS: accepted reference app/Explore references, adjustable sizing, desktop frame proportions/alignment and native Android behavior define the direction. |
| R-38 | PASS: normal APKs contain no seeded library or artwork. The optional native artwork check uses the existing credited desktop cache only in the disposable test package. |
| Liveliness: dials | PASS: 1/1/1 is explicit; quiet work surfaces, regular cards and native interaction motion match it. |
| Liveliness: focal point | PASS: media is primary in both board and full-screen viewer; Details and forms focus on one reference or action. |
| Liveliness: spacing | PASS: compact app bar, collection tabs, optional search, equal gutters and reserved floating-action space establish hierarchy; system insets remain outside content. |
| Liveliness: accent/motif | PASS: desktop green marks actions/selection; Papan's wordmark, collection strip and aligned media frames with integrated title/source captions supply the motif. |
| C-1 | PASS: the accepted visual reference, native guidance and desktop grid explain the documented choices. |
| C-2 | PASS: every action family connects to implemented storage, navigation, media or receiver behavior. |
| C-3 | PASS: each view supports collections, references, connections or pending delivery. |
| C-4 | PASS: backgrounding/configuration recreation retains app-session access; app close ends it. Both video orientations restore selection/position/pause. Native portrait frames align in rows/columns, while prior artwork square/three-size/long-caption/narrow/tablet/200% checks still pass. Captions fit on one line even in short frames. Same-bitmap rebind, bounded cache/measurement invalidation, visible-anchor retention, saved sizing, pagination and recycled off-screen cards pass. Connections/viewer feedback remains in its focused window. |
| C-5 | PASS: final signed normal/test classes and embedded files match, both apps are installed with matching bytes and backups, and the Samsung cold-starts/loads its protected desktop prompt over Wi-Fi with existing pairing and no USB tunnel. New lifecycle/decoded-video/cache checks remain isolated native evidence; physical protected media/lifecycle/new-pair approval, optical QR and other explicit gaps remain open. |

The final native report records the tested APK and provider hashes. `artifacts/android/media-session-{report,instrumentation,integrity,phone-install,physical-lan,cleanup}.*` retains the checks and installed-byte/data-identity evidence. Native captures include decoded artworks at three sizes, light/dark modes, portrait video frames and full-screen image/video viewers; video captures include actual copied native surface pixels. Existing dialog-feedback/nearby/discovery regressions remain passing. The prior eighteen contrast pairs are retained, with the additional play-indicator calculation recorded alongside them. The dark green is desktop's `#D0F5AD`, with accessible light-theme roles. [Verification](verification.md#explore-viewing-and-app-sessions-15-october-2026) records exact builds, installed backups, CI results and physical-versus-isolated scope. This gate verifies implementation and access; aesthetic acceptance remains the user's judgment.
