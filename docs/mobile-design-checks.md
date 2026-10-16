# Mobile design delivery gate

**PASS: bottom controls on scroll, 16 October 2026.** Direction: the user's mymind visual reference and Instagram Explore interaction reference, adjustable aligned desktop-style frames, lighter single-line captions, desktop green and native Android behavior. Both bottom controls slide out on downward scrolling and return upward; polling preserves the hidden row and visible reference. New boards and keyboard focus restore controls, and native touch exploration keeps them visible. Compact 40dp normal faces/48dp targets, scalable labels, stable menus and measured clearance remain. Complete preview rows meet both content edges and fill their tiles. ENERGY **1**, RHYTHM **1**, MOTION **2**: the requested brief slide clears media and locates returning controls. The location menu still reveals above its button. Behavior is verified only in the emulator; the signed update is installed over USB with matching package bytes and preserved app data identity. No phone app launch or UI/behavior test was performed. Desktop runtime/protocol and stored data remain unchanged. [Implementation direction](mobile-design.md) and [native evidence/limits](verification.md#bottom-controls-on-scroll-16-october-2026).

This report covers the changed mobile interface. Native Material components provide the platform interaction patterns; Papan supplies its identity and content. A physical screen-reader/soft-keyboard session is still a device follow-up, not a claimed result.

| Rule | Status and evidence |
| --- | --- |
| R-01 | PASS: desktop-style continuous fading separates media from readable captions; the design note records the phone contrast adjustment. No solid caption block or decorative gradient/glow. |
| R-02 | PASS: changed UI copy contains no em dashes. |
| R-03 | PASS: native 40dp normal faces retain 48dp targets, stable labels/menu sizes and measured bottom clearance across narrow/normal/tablet/200% text. Outside-face Add taps succeed. Edge-to-edge rows, safe areas and popup non-overlap pass. |
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
| R-14 | PASS: consistent reference cards support scanning a collection; the full-screen fitted viewer, separate Details sheet and forms follow their content and actions. |
| R-15 | PASS: actions name their result, including Paste link, Choose collection, Retry preview and Save changes. |
| R-16 | PASS: no AI marketing claims or buzzwords. |
| R-17 | PASS: no invented statistics; documented bounds and test counts come from source/test output. |
| R-18 | PASS: no testimonials or fictional people. |
| R-19 | PASS: native themed hide-on-scroll motion clears previews and locates returning controls, matching MOTION 2. Real gestures/bounds and native before/hidden/returned captures verify the result. Prior compositor frames retain their scope for the unchanged location-menu reveal. No decorative or looping animation. |
| R-20 | PASS: notebook surfaces, desktop green, Papan wordmark, centered collection dropdown and touching aligned previews form the requested compact identity. |
| R-21 | PASS: working Use phone setting/Light/Dark controls persist the choice and recreate the native Activity. |
| R-22 | PASS: no generated illustrations. |
| R-23 | PASS: the user chose mymind styling and Explore-style viewing with Papan alignment/sizing. Existing Papan identity, licensed Material vectors and credited public-domain artwork fixtures are used; no identity imagery is generated. |
| R-24 | PASS: Phone replaces On phone in navigation/save choices. The current location stays on its tappable green button; the menu has only the other two alternatives. Collection selection/UUID and green selected-row behavior pass. |
| R-25 | PASS: prior 18 text/control pairs and play-indicator evidence remain valid. Ten new dropdown/location/highlight/focus pairs in light/dark themes have a minimum 5.56:1 contrast. Actual caption fade pixels retain AA over pure white; theme tints and field/card focus remain readable. |
| R-26 | PASS: native card bounds meet on both axes, retaining aligned sizes and desktop portrait proportions. Saved/manual desktop order, five-second rearrangement refresh across 85 loaded pins, pagination/search/swipes, alternating two authorized collections, background/rotation retention and app-close relocking pass. Prior actual video pixels, controls/restoration/zoom/cache, collection/capture/paste/share/edit/retry/delete/nearby/QR/recovery/delivery checks pass with three real saves. |
| R-27 | PASS: Connections feedback scrolls inside its focused dialog at normal/200% fonts; board feedback waits for forms to close. Viewer boundary feedback appears inside the viewer. Posters/loading, playback error/Retry, offline, empty, locked and validation states are implemented. Protected thumbnails and measured proportions clear on desktop lock/close/revoke/unavailability. Verified endpoint recovery preserves keys/destinations and rejects wrong identities/stale manual-edit races. A corrupt-video fixture is not claimed. |
| R-28 | PASS: no generic FAQ. |
| R-29 | PASS: neutral notebook surfaces and one desktop green action accent define light/dark palettes; muted secondary navigation supports selection. |
| R-30 | PASS: mymind styling and Explore viewing are requested references; Papan retains its wordmark, adjustable aligned frames, compact location/collection controls, green highlights and native Android interactions. |
| R-31 | PASS: component purposes, spacing, typography, color and responsive behavior are documented in the design note. |
| R-32 | PASS: keyboard focus restores hidden controls; Tab/Enter opens Add across narrow/normal/tablet/200% text. Full native focus/activation/dismissal and current-button tapping pass. Popup geometry excludes the button. Native touch-exploration protection remains enabled; actual screen-reader interaction remains unverified. This follow-up used only the emulator. |
| R-33 | PASS: Java/XML directly implement the interface; the build compiles that source without UI rewriting scripts. |
| R-34 | PASS: Light/Dark/System controls, fresh theme initialization, restored selected collection/size, and readable toolbar icons pass native checks. |
| R-35 | PASS: focused native suite starts on private display 11 and verifies real scroll direction, both controls leaving/returning to the viewport, refresh pin/offset retention, reversal, keyboard recovery, new empty boards and compact sizing across layouts. Native captures were inspected. Full native suite starts on display 15 and passes remote-board scrolling plus existing edge-to-edge/crop/portrait/media/order/grants/recovery/design and three saves. Normal/test builds share 42 classes/27 Android files/44 exact total source files and verify v2/v3 against the retained certificate. All three runners reject physical targets; the new guard was exercised before ADB. Desktop runtime retains prior evidence. |
| R-36 | PASS: trusted-LAN HTTP and capture limits are disclosed. No certification or performance claims. |
| R-37 | PASS: accepted mymind/Explore references, aligned frames, gap-free pins, compact dropdowns/green highlights and the explicit scroll-hiding request define the current direction. The design note records 1/1/2 before implementation. |
| R-38 | PASS: normal APKs contain no seeded library or artwork. The optional native artwork check uses the existing credited desktop cache only in the disposable test package. |
| Liveliness: dials | PASS: 1/1/2 is explicit; quiet work surfaces, regular cards and the requested functional scroll slide match it. |
| Liveliness: focal point | PASS: media is primary in both board and full-screen viewer; Details and forms focus on one reference or action. |
| Liveliness: spacing | PASS: one compact app bar, optional search, touching media and a shared floating-action row free preview space; system insets and bottom scroll padding keep content reachable. |
| Liveliness: accent/motif | PASS: desktop green marks actions and selected menu rows; Papan's wordmark, collection dropdown and aligned frames with integrated captions supply the motif. |
| C-1 | PASS: the accepted visual reference, native guidance and desktop grid explain the documented choices. |
| C-2 | PASS: every action family connects to implemented storage, navigation, media or receiver behavior. |
| C-3 | PASS: each view supports collections, references, connections or pending delivery. |
| C-4 | PASS: native scrolling/reversal/refresh retention/keyboard recovery and new empty boards pass across narrow/normal/tablet/200% layouts. Compact faces, 48dp targets, scalable labels, clearance, full-width frames, menus and existing local/desktop/media/artwork/theme checks pass. |
| C-5 | PASS: actual native gestures/bounds/captures prove scroll hiding, recovery and preserved navigation; decoded-image/workflow assertions remain passing. Signed build/source checks pass. USB installation matches package bytes and preserves app data identity; no phone app launch or UI/behavior test is claimed. Documented device/platform limits remain. |

The exact APK/provider hashes are recorded in `artifacts/android/scroll-controls-{report,instrumentation,integrity,focused-report,focused-instrumentation,phone-install,cleanup}.*` and `scroll-controls/`. Earlier `compact-controls-*`, `edge-preview-*`, `preview-fill-*` and `menu-reveal-*` records retain their dated scope. Actual native window/video captures verify rendering; popup captures draw settled native trees. Prior AA palette/menu-motion evidence remains valid; caption pixels are checked again. [Verification](verification.md#bottom-controls-on-scroll-16-october-2026) records builds, gesture scope and emulator-only limits. This gate verifies implementation and access; aesthetic acceptance remains the user's judgment.
