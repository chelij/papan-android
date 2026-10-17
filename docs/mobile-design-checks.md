# Mobile design delivery gate

**PASS: Phone portrait frames and local autoplay, 17 October 2026.** Android source `40f937d` uses complete-collection local media proportions and muted visible-only saved video playback. Exact final API 36 native checks pass on private display 9, with decoded playback/looping, offscreen/viewer/background decoder release/return, shared crop proportions, file replacement, bitmap reuse and narrow/200% text. Full desktop-backed checks pass on display 12, including three receiver saves and existing order/password/menu/theme/bar/caption behavior. Both signed variants share 52 application classes, 57 exact source files and 39 unchanged runtime assets. The normal APK is installed with verified bytes/certificate and preserved data identity; no phone UI was used. See [current verification](verification.md#phone-portrait-frames-and-local-autoplay-17-october-2026) and `artifacts/phone-portrait-frames/`.

The prior media-selection/popup report remains valid for unchanged controls and byte-identical extraction/storage/viewer classes, linked through the current integrity record. The gate below combines those retained checks with the new native frame/playback and complete app workflow. ENERGY 1 / RHYTHM 1 / MOTION 2 remains the accepted direction: motion comes from requested media playback and functional bar movement.

This report covers the changed mobile interface. Native Material components provide the platform interaction patterns; Papan supplies its identity and content. A physical screen-reader/soft-keyboard session is still a device follow-up, not a claimed result.

| Rule | Status and evidence |
| --- | --- |
| R-01 | PASS: desktop-style continuous fading separates media from readable captions; the design note records the phone contrast adjustment. No solid caption block or decorative gradient/glow. |
| R-02 | PASS: changed UI copy contains no em dashes. |
| R-03 | PASS: portrait and mixed Phone frames remain aligned and edge-to-edge, including narrow/200% text; full workflow checks 300/360/640dp layouts. Existing distinct/wider popup row and short-menu evidence is retained for unchanged picker code.
| R-04 | PASS: licensed Material phone, desktop, history, search, add, close and play vectors identify real actions; the dropdown arrow comes from the existing Material library. Protected collections retain explicit text status. No new assets/dependencies. |
| R-05 | PASS: the app-bar collection picker, media board, floating location/Add row, native menus, sheets and details serve distinct purposes. No marketing template. |
| R-06 | PASS: native scalable sans-serif follows Android guidance and desktop's sans-serif identity. |
| R-07 | PASS: no decorative background grid or pattern. |
| R-08 | PASS: no decorative arrow buttons. |
| R-09 | PASS: no promotional badges. Collection protection is stated in its menu label. |
| R-10 | PASS: no glassmorphism. |
| R-11 | PASS: square tile corners allow adjacent media to meet; native popup/dialog/sheet and floating-action shapes follow their component purposes. |
| R-12 | PASS: cards have zero elevation; standard floating actions and dialogs retain their platform hierarchy. |
| R-13 | PASS: no decorative glow layers. |
| R-14 | PASS: aligned cards scan the board; fitted full-screen media, one native overflow, read-only Details and edit/save forms serve distinct content and actions. |
| R-15 | PASS: core desktop action names Edit pin, Remove pin, Copy link and Open original identify their result. Refresh saved media and Retry download name the phone capture actions. |
| R-16 | PASS: no AI marketing claims or buzzwords. |
| R-17 | PASS: no invented statistics; documented bounds and test counts come from source/test output. |
| R-18 | PASS: no testimonials or fictional people. |
| R-19 | PASS: requested local videos autoplay muted/looping only while visible and release decoders offscreen, behind the viewer or in background. Shared native bars retain functional synchronized motion; the design note gives both purposes under MOTION 2.
| R-20 | PASS: notebook surfaces, desktop green, Papan wordmark, centered collection dropdown and touching aligned previews form the requested compact identity. |
| R-21 | PASS: working Use phone setting/Light/Dark controls persist the choice and recreate the native Activity. |
| R-22 | PASS: no generated illustrations. |
| R-23 | PASS: the user chose reference app styling and Explore-style viewing with Papan alignment/sizing. Existing Papan identity, licensed Material vectors and credited public-domain artwork fixtures are used; no identity imagery is generated. |
| R-24 | PASS: location choice, current-button dismissal and selection pass. Native pin overflow opens actual implemented actions, while unsupported desktop editing/removal/pose actions stay on desktop under existing consent. |
| R-25 | PASS: declared native light/dark text/action/selection roles and rendered selected-card contrast exceed 4.5:1; full native workflow retains the measured white-media caption fade contrast. See contrast.json and the existing caption evidence. |
| R-26 | PASS: media cards, Choose collection, Cancel, link-only/desktop delivery and 503 → Retry recovery work through native controls. Actual files, offline decoding, shared-bar behavior and three receiver saves pass. |
| R-27 | PASS: loading, source error/Retry, unselected/selected and pending/failed/saved states have native labels/actions. Download and rejected-commit checks retain saved files and clean staged media. |
| R-28 | PASS: no generic FAQ. |
| R-29 | PASS: neutral notebook surfaces and one desktop green action accent define light/dark palettes; muted secondary navigation supports selection. |
| R-30 | PASS: reference app styling and Explore viewing are requested references; Papan retains its wordmark, adjustable aligned frames, compact location/collection controls, green highlights and native Android interactions. |
| R-31 | PASS: component purposes, spacing, typography, color and responsive behavior are documented in the design note. |
| R-32 | PASS: native card/checkable descriptions, focus and scalable labels remain. Enlarged-text chooser actions scroll fully into view; full keyboard/menu checks and alternate viewer navigation pass. Physical screen-reader coverage is not claimed. |
| R-33 | PASS: Java/XML directly implement the interface; the build compiles that source without UI rewriting scripts. |
| R-34 | PASS: Light/Dark/System controls, fresh theme initialization, restored selected collection/size, and readable toolbar icons pass native checks. |
| R-35 | PASS: exact final APK passes native Phone frame/playback checks on private display 9 and full desktop-backed workflow on 12, with three receiver saves. Native texture pixels, advancing/looping media, crop, decoder teardown/return, captions and responsive layouts verify. Both signed variants share 52 application classes/57 source files/39 runtime assets. Installed bytes/certificate and data identity verify without phone UI access.
| R-36 | PASS: trusted-LAN HTTP and capture limits are disclosed. No certification or performance claims. |
| R-37 | PASS: accepted reference app/Explore references, aligned gap-free frames, compact dropdowns/green highlights and the explicit synchronized top/bottom motion request define the direction. The design note and task record retain 1/1/2 before implementation. |
| R-38 | PASS: normal APKs contain no seeded library or artwork. The optional native artwork check uses the existing credited desktop cache only in the disposable test package. |
| Liveliness: dials | PASS: 1/1/2 is explicit; quiet work surfaces, regular cards and the requested functional scroll slide match it. |
| Liveliness: focal point | PASS: media is primary in both board and full-screen viewer; Details and forms focus on one reference or action. |
| Liveliness: spacing | PASS: one compact app bar, optional search, touching media and a shared floating-action row free preview space; system insets and bottom scroll padding keep content reachable. |
| Liveliness: accent/motif | PASS: desktop green marks actions and selected menu rows; Papan's wordmark, collection dropdown and aligned frames with integrated captions supply the motif. |
| C-1 | PASS: the accepted visual reference, native guidance and desktop grid explain the documented choices. |
| C-2 | PASS: every chooser action completes a native selection, retry, cancel or destination operation. Pin menus and actual desktop delivery remain functional. |
| C-3 | PASS: each view supports collections, references, connections or pending delivery. |
| C-4 | PASS: native labels/menu bounds, distinct row fonts, narrow/200% actions, selection/error recovery, download/commit rollback, offline attachments and inherited theme/keyboard paths pass in the recorded scope. |
| C-5 | PASS: exact APK/source/runtime/signature and installed-byte evidence is recorded. Real photo-sharing service data is separate from synthetic offline fixtures, and failed width/motion-verifier attempts remain. No physical-phone UI or broad site guarantee is claimed. |

Current evidence is recorded in `artifacts/mobile-video-menus/{integrity,storage,capture,links,pin-menu,menu}.*`, `artifacts/android/{pin-menu,location-menu}/` and desktop `artifacts/android/ui/`. Earlier synchronized-bar, caption/contrast and publication artifacts retain their dated scope in the verification document. Palette, resource bytes and identity remain unchanged. All 38 R rules, five C checks and liveliness checks pass for this scoped update; no new release is claimed. Aesthetic acceptance remains the user's judgment.

The location sizing follow-up is recorded in `artifacts/location-text-width/{integrity,visual-inspection,baseline-evidence,phone-install,cleanup}.json` and its copied native menu/scroll evidence. All R-1–R-38, C-1–C-5 and liveliness checks remain PASS within the current or explicitly reused scope.

Current follow-up evidence: `artifacts/popup-video-extractor/`. All R-1–R-38, C-1–C-5 and liveliness checks remain PASS within the current or explicitly reused scope. The earlier reports retain their dated bounds.
