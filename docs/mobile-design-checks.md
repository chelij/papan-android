# Mobile design delivery gate

**PASS: location button sizing, 17 October 2026.** Native content measurement reserves the widest location label and keeps compact controls in one row. Settled native layouts pass at 300/360/640dp, 200% text and a late 30% wider font metric; the actual emulator window shows Desktop in full. Menu reveal/scrolling, synchronized bars, focus and touch targets pass on private displays 24/30. The old source also passes these AOSP cases, so no Samsung-specific reproduction is claimed. The signed APK is installed in place with matching read-back bytes and preserved data identity; no phone UI has been operated. Direction remains reference app/photo-sharing service Explore, aligned adjustable desktop frames, lighter one-line captions, desktop green and Android Material, at ENERGY **1**, RHYTHM **1**, MOTION **2**. Unchanged palette, media and full-workflow evidence retains its dated scope. [Current evidence and limits](verification.md#location-button-sizing-17-october-2026).

This report covers the changed mobile interface. Native Material components provide the platform interaction patterns; Papan supplies its identity and content. A physical screen-reader/soft-keyboard session is still a device follow-up, not a claimed result.

| Rule | Status and evidence |
| --- | --- |
| R-01 | PASS: desktop-style continuous fading separates media from readable captions; the design note records the phone contrast adjustment. No solid caption block or decorative gradient/glow. |
| R-02 | PASS: changed UI copy contains no em dashes. |
| R-03 | PASS: native location labels remain one line without ellipsis at 300/360/640dp and 200% text. A 210dp-high display scrolls/selects its second destination. Full native responsive/portrait/edge-to-edge/48dp target checks pass; glyph rounding has a measured width allowance. |
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
| R-19 | PASS: native upward location-menu compositor frames keep the button uncovered. Existing shared bar progress passes the full remote workflow; unchanged scroll implementation retains its dated 16 October native/pixel measurements. Menu actions close before opening another sheet; no decorative motion is added. |
| R-20 | PASS: notebook surfaces, desktop green, Papan wordmark, centered collection dropdown and touching aligned previews form the requested compact identity. |
| R-21 | PASS: working Use phone setting/Light/Dark controls persist the choice and recreate the native Activity. |
| R-22 | PASS: no generated illustrations. |
| R-23 | PASS: the user chose reference app styling and Explore-style viewing with Papan alignment/sizing. Existing Papan identity, licensed Material vectors and credited public-domain artwork fixtures are used; no identity imagery is generated. |
| R-24 | PASS: location choice, current-button dismissal and selection pass. Native pin overflow opens actual implemented actions, while unsupported desktop editing/removal/pose actions stay on desktop under existing consent. |
| R-25 | PASS: prior 18 text/control pairs and play-indicator evidence remain valid. Ten new dropdown/location/highlight/focus pairs in light/dark themes have a minimum 5.56:1 contrast. Actual caption fade pixels retain AA over pure white; theme tints and field/card focus remain readable. |
| R-26 | PASS: full native workflow routes overflow edit/copy/browser/details/save/remove/cancel/retry and vertical/horizontal swipes without previous/next controls. Three focused copy/details/close cycles and save-sheet cancellation preserve viewer/menu focus. Video captures before its poster and plays after source downloads are disabled; three actual desktop saves and existing order/grant/preview workflows pass. |
| R-27 | PASS: Details contains full title/source/notes and capture errors. Retry download keeps the link and existing video; new failed-video pins retain an available poster. Native HTTP/storage tests verify invalid/oversized fallback and atomic retry cleanup. Existing loading, playback error, empty/offline/locked and validation states pass. |
| R-28 | PASS: no generic FAQ. |
| R-29 | PASS: neutral notebook surfaces and one desktop green action accent define light/dark palettes; muted secondary navigation supports selection. |
| R-30 | PASS: reference app styling and Explore viewing are requested references; Papan retains its wordmark, adjustable aligned frames, compact location/collection controls, green highlights and native Android interactions. |
| R-31 | PASS: component purposes, spacing, typography, color and responsive behavior are documented in the design note. |
| R-32 | PASS: native menu/collection/Add keyboard paths pass in the full workflow. Media-stage arrow keys and Android accessibility scroll actions preserve navigation without visible previous/next controls; existing zoom/seek focus remains. Physical screen-reader coverage remains a documented limit. |
| R-33 | PASS: Java/XML directly implement the interface; the build compiles that source without UI rewriting scripts. |
| R-34 | PASS: Light/Dark/System controls, fresh theme initialization, restored selected collection/size, and readable toolbar icons pass native checks. |
| R-35 | PASS: current sizing checks pass on private displays 24/30 with a native-window label capture, 48 identical common application classes and 48 exact embedded source files. Both v2/v3 maintainer signatures verify. Prior full workflow/media/contrast evidence is reused only for unchanged sources. Installed APK bytes and certificate match; app/data identity is retained. Phone UI and Samsung font behavior are excluded from testing. |
| R-36 | PASS: trusted-LAN HTTP and capture limits are disclosed. No certification or performance claims. |
| R-37 | PASS: accepted reference app/Explore references, aligned gap-free frames, compact dropdowns/green highlights and the explicit synchronized top/bottom motion request define the direction. The design note and task record retain 1/1/2 before implementation. |
| R-38 | PASS: normal APKs contain no seeded library or artwork. The optional native artwork check uses the existing credited desktop cache only in the disposable test package. |
| Liveliness: dials | PASS: 1/1/2 is explicit; quiet work surfaces, regular cards and the requested functional scroll slide match it. |
| Liveliness: focal point | PASS: media is primary in both board and full-screen viewer; Details and forms focus on one reference or action. |
| Liveliness: spacing | PASS: one compact app bar, optional search, touching media and a shared floating-action row free preview space; system insets and bottom scroll padding keep content reachable. |
| Liveliness: accent/motif | PASS: desktop green marks actions and selected menu rows; Papan's wordmark, collection dropdown and aligned frames with integrated captions supply the motif. |
| C-1 | PASS: the accepted visual reference, native guidance and desktop grid explain the documented choices. |
| C-2 | PASS: every pin action routes to a native viewer/detail/edit/save/remove/retry/copy/browser operation. Three focused popup cycles and the complete native workflow pass; consent-limited desktop actions are omitted. |
| C-3 | PASS: each view supports collections, references, connections or pending delivery. |
| C-4 | PASS: one-line labels, constrained menu scrolling, repeated popup transitions, 48dp targets and narrow/tablet/200% text pass. Full native protected/local media, responsive/theme/artwork and shared-bar checks pass. Previous/next controls are absent; keyboard/accessibility alternatives remain. |
| C-5 | PASS: reports identify exact local sources and APKs, decoded video pixels and offline playback, actual receiver saves and separate failed/finished attempts. An unrelated randomized QR failure is retained; the unchanged final rerun passes. No phone UI or physical performance claim is made. |

Current evidence is recorded in `artifacts/mobile-video-menus/{integrity,storage,capture,links,pin-menu,menu}.*`, `artifacts/android/{pin-menu,location-menu}/` and desktop `artifacts/android/ui/`. Earlier synchronized-bar, caption/contrast and publication artifacts retain their dated scope in the verification document. Palette, resource bytes and identity remain unchanged. All 38 R rules, five C checks and liveliness checks pass for this scoped update; no new release is claimed. Aesthetic acceptance remains the user's judgment.

The location sizing follow-up is recorded in `artifacts/location-text-width/{integrity,visual-inspection,baseline-evidence,phone-install,cleanup}.json` and its copied native menu/scroll evidence. All R-1–R-38, C-1–C-5 and liveliness checks remain PASS within the current or explicitly reused scope.
