# Mobile design delivery gate

**PASS, 15 October 2026.** Direction: the user's reference app visual reference and photo-sharing service Explore interaction reference, adjustable aligned desktop-style frames, lighter single-line title/source captions, desktop green and standard Android Material behavior. The latest direction removes pin gutters, moves collections into the app bar, combines the three locations beside Add and uses green row highlights instead of dropdown checkboxes. ENERGY **1**, RHYTHM **1**, MOTION **1**. Desktop runtime/protocol and stored data remain unchanged. [Implementation direction](mobile-design.md) and [native evidence/limits](verification.md#compact-board-navigation-15-october-2026).

This report covers the changed mobile interface. Native Material components provide the platform interaction patterns; Papan supplies its identity and content. A physical screen-reader/soft-keyboard session is still a device follow-up, not a claimed result.

| Rule | Status and evidence |
| --- | --- |
| R-01 | PASS: desktop-style continuous fading separates media from readable captions; the design note records the phone contrast adjustment. No solid caption block or decorative gradient/glow. |
| R-02 | PASS: changed UI copy contains no em dashes. |
| R-03 | PASS: native 300dp/640dp/200% layouts reflow; app-bar dropdown, location/Add bounds, shared bottom row and 48dp targets are asserted. Native popup bounds fit the window. |
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
| R-19 | PASS: standard Material interaction motion only; no decorative entrance/loop animations. |
| R-20 | PASS: notebook surfaces, desktop green, Papan wordmark, centered collection dropdown and touching aligned previews form the requested compact identity. |
| R-21 | PASS: working Use phone setting/Light/Dark controls persist the choice and recreate the native Activity. |
| R-22 | PASS: no generated illustrations. |
| R-23 | PASS: the user chose reference app styling and Explore-style viewing with Papan alignment/sizing. Existing Papan identity, licensed Material vectors and credited public-domain artwork fixtures are used; no identity imagery is generated. |
| R-24 | PASS: the single location chooser selects On phone, Desktop and Activity. The header dropdown selects actual collection IDs; reordering collections during an open menu preserves the chosen UUID. Current rows use a green highlight with native selection semantics and no checkbox. |
| R-25 | PASS: prior 18 text/control pairs and play-indicator evidence remain valid. Ten new dropdown/location/highlight/focus pairs in light/dark themes have a minimum 5.56:1 contrast. Actual caption fade pixels retain AA over pure white; theme tints and field/card focus remain readable. |
| R-26 | PASS: native card bounds meet on both axes, retaining aligned sizes and desktop portrait proportions. Saved/manual desktop order, five-second rearrangement refresh across 85 loaded pins, pagination/search/swipes, alternating two authorized collections, background/rotation retention and app-close relocking pass. Prior actual video pixels, controls/restoration/zoom/cache, collection/capture/paste/share/edit/retry/delete/nearby/QR/recovery/delivery checks pass with three real saves. |
| R-27 | PASS: Connections feedback scrolls inside its focused dialog at normal/200% fonts; board feedback waits for forms to close. Viewer boundary feedback appears inside the viewer. Posters/loading, playback error/Retry, offline, empty, locked and validation states are implemented. Protected thumbnails and measured proportions clear on desktop lock/close/revoke/unavailability. Verified endpoint recovery preserves keys/destinations and rejects wrong identities/stale manual-edit races. A corrupt-video fixture is not claimed. |
| R-28 | PASS: no generic FAQ. |
| R-29 | PASS: neutral notebook surfaces and one desktop green action accent define light/dark palettes; muted secondary navigation supports selection. |
| R-30 | PASS: reference app styling and Explore viewing are requested references; Papan retains its wordmark, adjustable aligned frames, compact location/collection controls, green highlights and native Android interactions. |
| R-31 | PASS: component purposes, spacing, typography, color and responsive behavior are documented in the design note. |
| R-32 | PASS: actual Tab/Enter/Back open and dismiss native dropdowns. Arrow keys move a separately highlighted cursor while the current location retains its green selected row. Existing zoom/seek/navigation alternatives and card/form focus remain available. |
| R-33 | PASS: Java/XML directly implement the interface; the build compiles that source without UI rewriting scripts. |
| R-34 | PASS: Light/Dark/System controls, fresh theme initialization, restored selected collection/size, and readable toolbar icons pass native checks. |
| R-35 | PASS: the full final Android 16/API 36 native run passes on isolated display 16, including fresh initialization, actual video pixels, menus/grid/order/access/cache and three receiver saves. Signed variants share 41 application classes/27 embedded source files. The Samsung update has matching installed bytes and preserved data identity. Unchanged desktop runtime retains its 71 unit groups/twelve receiver groups, native source/package and Linux/Windows/macOS CI evidence. |
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
| C-4 | PASS: touching cards, header selection, bottom-row controls and popup bounds fit narrow/tablet/200% layouts; tablets regain the full board width and five default columns. Foreground refresh preserves desktop pin order and an open menu's collection identity. Independent grants, viewer/portrait/cache/scroll-anchor/pagination, three artwork sizes and themed feedback retain their native evidence. |
| C-5 | PASS: final signed normal/test classes and embedded files match; Samsung installed bytes and app data identity are verified with a named rollback APK. USB-controlled physical checks confirm cold start, all three locations, both checkbox-free dropdowns, aligned bottom controls and protected desktop prompting over Wi-Fi without reverse tunnels. Test state/private UI files are cleaned. Protected media/lifecycle/rearrangement remain isolated native evidence; physical protected-media, TalkBack/soft keyboard, optical QR, Android 8 and published-pair limits remain open. |

The final native report records the tested APK and unchanged provider hashes. `artifacts/android/compact-board-{report,instrumentation,integrity,contrast,phone-install,physical-lan,cleanup}.*` holds current evidence. Native board/viewer captures contain decoded artwork and actual copied video-surface pixels; menu captures draw the actual native popup view tree after its entrance animation settles. Prior eighteen contrast pairs/play-indicator evidence remain valid, with ten new control/highlight/focus pairs recorded. The dark accent is desktop's `#D0F5AD`, with accessible light-theme roles. [Verification](verification.md#compact-board-navigation-15-october-2026) records exact builds, backups, reused desktop CI and physical-versus-isolated scope. This gate verifies implementation and access; aesthetic acceptance remains the user's judgment.
