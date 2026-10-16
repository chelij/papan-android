# Mobile design delivery gate

**PASS: edge-to-edge previews, 16 October 2026.** Direction: the user's reference app visual reference and photo-sharing service Explore interaction reference, adjustable aligned desktop-style frames, lighter single-line captions, desktop green and native Android behavior. Complete preview rows reach both content edges without side padding and retain native safe areas. Previews zoom and crop to fill those tiles by default, including desktop whole-image collections; explicit whole-image preferences remain available and persistent. The current location is Phone, Desktop or Activity, shown on a tappable green button with only the other two locations in its menu. Picker labels share scalable Material Body Large typography and stable dimensions across locations. ENERGY **1**, RHYTHM **1**, MOTION **1**. The location menu reveals upward from behind its button and stays above it. Behavior is verified only on an emulator; the finished APK was then installed on the phone without launching Papan or testing its UI. Desktop runtime/protocol and stored data remain unchanged. [Implementation direction](mobile-design.md) and [native evidence/limits](verification.md#edge-to-edge-previews-16-october-2026).

This report covers the changed mobile interface. Native Material components provide the platform interaction patterns; Papan supplies its identity and content. A physical screen-reader/soft-keyboard session is still a device follow-up, not a claimed result.

| Rule | Status and evidence |
| --- | --- |
| R-01 | PASS: desktop-style continuous fading separates media from readable captions; the design note records the phone contrast adjustment. No solid caption block or decorative gradient/glow. |
| R-02 | PASS: changed UI copy contains no em dashes. |
| R-03 | PASS: native complete-row bounds meet both grid edges without horizontal padding across preview sizes and narrow/normal/tablet/200% text. Stable controls/type, 48dp targets, bottom clearance and popup bounds pass; Android safe areas remain. Location popup bounds stay above its button. |
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
| R-19 | PASS: the location menu uses native interactive reveal motion from the button edge; no decorative or looping animation. Compositor frames verify upward motion and the uncovered button. |
| R-20 | PASS: notebook surfaces, desktop green, Papan wordmark, centered collection dropdown and touching aligned previews form the requested compact identity. |
| R-21 | PASS: working Use phone setting/Light/Dark controls persist the choice and recreate the native Activity. |
| R-22 | PASS: no generated illustrations. |
| R-23 | PASS: the user chose reference app styling and Explore-style viewing with Papan alignment/sizing. Existing Papan identity, licensed Material vectors and credited public-domain artwork fixtures are used; no identity imagery is generated. |
| R-24 | PASS: Phone replaces On phone in navigation/save choices. The current location stays on its tappable green button; the menu has only the other two alternatives. Collection selection/UUID and green selected-row behavior pass. |
| R-25 | PASS: prior 18 text/control pairs and play-indicator evidence remain valid. Ten new dropdown/location/highlight/focus pairs in light/dark themes have a minimum 5.56:1 contrast. Actual caption fade pixels retain AA over pure white; theme tints and field/card focus remain readable. |
| R-26 | PASS: native card bounds meet on both axes, retaining aligned sizes and desktop portrait proportions. Saved/manual desktop order, five-second rearrangement refresh across 85 loaded pins, pagination/search/swipes, alternating two authorized collections, background/rotation retention and app-close relocking pass. Prior actual video pixels, controls/restoration/zoom/cache, collection/capture/paste/share/edit/retry/delete/nearby/QR/recovery/delivery checks pass with three real saves. |
| R-27 | PASS: Connections feedback scrolls inside its focused dialog at normal/200% fonts; board feedback waits for forms to close. Viewer boundary feedback appears inside the viewer. Posters/loading, playback error/Retry, offline, empty, locked and validation states are implemented. Protected thumbnails and measured proportions clear on desktop lock/close/revoke/unavailability. Verified endpoint recovery preserves keys/destinations and rejects wrong identities/stale manual-edit races. A corrupt-video fixture is not claimed. |
| R-28 | PASS: no generic FAQ. |
| R-29 | PASS: neutral notebook surfaces and one desktop green action accent define light/dark palettes; muted secondary navigation supports selection. |
| R-30 | PASS: reference app styling and Explore viewing are requested references; Papan retains its wordmark, adjustable aligned frames, compact location/collection controls, green highlights and native Android interactions. |
| R-31 | PASS: component purposes, spacing, typography, color and responsive behavior are documented in the design note. |
| R-32 | PASS: full native keyboard focus/activation/dismissal and focused current-button tapping pass. Popup geometry excludes the button across every checked layout. Native pointer checks use unprotected fixtures; dated physical evidence remains historical and this follow-up used only the emulator. |
| R-33 | PASS: Java/XML directly implement the interface; the build compiles that source without UI rewriting scripts. |
| R-34 | PASS: Light/Dark/System controls, fresh theme initialization, restored selected collection/size, and readable toolbar icons pass native checks. |
| R-35 | PASS: latest full native suite on private display 3 measures actual left/right row edges across layouts/sizes and verifies decoded crop coverage, whole-image persistence, retained portrait frames and existing workflows. Prior display-6/13-frame menu-motion evidence retains its unchanged implementation scope. Normal/test builds share 41 classes/27 Android files/43 exact total source files and verify v2/v3 against the retained installation certificate. Both runners reject physical targets before ADB. Desktop runtime retains its prior exact-runtime evidence. |
| R-36 | PASS: trusted-LAN HTTP and capture limits are disclosed. No certification or performance claims. |
| R-37 | PASS: accepted reference app/Explore references, adjustable aligned frames, gap-free pins, an app-bar collection dropdown, one bottom-left location button and green row highlights define the current direction. |
| R-38 | PASS: normal APKs contain no seeded library or artwork. The optional native artwork check uses the existing credited desktop cache only in the disposable test package. |
| Liveliness: dials | PASS: 1/1/1 is explicit; quiet work surfaces, regular cards and native interaction motion match it. |
| Liveliness: focal point | PASS: media is primary in both board and full-screen viewer; Details and forms focus on one reference or action. |
| Liveliness: spacing | PASS: one compact app bar, optional search, touching media and a shared floating-action row free preview space; system insets and bottom scroll padding keep content reachable. |
| Liveliness: accent/motif | PASS: desktop green marks actions and selected menu rows; Papan's wordmark, collection dropdown and aligned frames with integrated captions supply the motif. |
| C-1 | PASS: the accepted visual reference, native guidance and desktop grid explain the documented choices. |
| C-2 | PASS: every action family connects to implemented storage, navigation, media or receiver behavior. |
| C-3 | PASS: each view supports collections, references, connections or pending delivery. |
| C-4 | PASS: full-width touching frames, header dropdown, stable location/button menus, shared typography, 48dp controls and dynamic bottom clearance fit narrow/tablet/200% layouts. Existing collection reorder, order/grants/media/cache/scroll/pagination/artwork/theme checks pass. |
| C-5 | PASS: native card bounds prove both outer row edges; decoded-image/actions retain cropping, whole-image persistence, portrait frames and navigation. Signed build/source checks pass. Subsequent authenticated USB installation matches the tested APK and retains app data identity; no phone app launch or UI/behavior test was performed. Documented device/platform limits remain. |

The exact APK/provider hashes are recorded in the full native report and `artifacts/android/edge-preview-{report,instrumentation,integrity,phone-install,cleanup}.*`. Earlier `preview-fill-*`, `menu-reveal-*` and `location-menu/` records retain their dated scope. Actual compositor animation captures and native window/video captures verify rendering; popup captures draw settled native trees. Prior AA palette/caption evidence remains valid. [Verification](verification.md#edge-to-edge-previews-16-october-2026) records builds, native frame scope and emulator-only limits. This gate verifies implementation and access; aesthetic acceptance remains the user's judgment.
