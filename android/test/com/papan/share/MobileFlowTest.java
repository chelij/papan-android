package com.papan.share;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Included only in the separate --test APK. Never operate the personal app or display 0.
public final class MobileFlowTest extends Instrumentation {
    private double captionTitleContrast = 21, captionSourceContrast = 21;
    private Bundle arguments;
    private volatile MainActivity main;
    private ScanActivity scan;
    private VirtualDisplay display;
    private ImageReader output;
    private ClipboardManager clipboard;
    private ClipData previous;
    private String clipboardFixture;
    private int displayId;
    private float testFontScale = 1;
    private final int[] barPixels = new int[6];
    @Override public Activity newActivity(ClassLoader loader, String name, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException { Activity activity = super.newActivity(loader, name, intent); if (activity instanceof MainActivity) { android.content.res.Configuration config = new android.content.res.Configuration(); config.fontScale = testFontScale; activity.applyOverrideConfiguration(config); } return activity; }
    @Override public void callActivityOnCreate(Activity activity, Bundle saved) { super.callActivityOnCreate(activity, saved); if (activity instanceof MainActivity) main = (MainActivity)activity; }
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        if ("true".equals(arguments.getString("scrollOnly"))) { scrollOnly(); return; }
        if ("true".equals(arguments.getString("locationOnly"))) { locationOnly(); return; }
        if ("true".equals(arguments.getString("viewerMenuOnly"))) { viewerMenuOnly(); return; }
        Bundle result = new Bundle();
        boolean cameraOnly = "true".equals(arguments.getString("cameraOnly"));
        try {
            require("com.papan.share.test".equals(getTargetContext().getPackageName()), "Only the disposable test package is allowed.");
            require(cameraOnly || !((KeyguardManager)getTargetContext().getSystemService(Context.KEYGUARD_SERVICE)).isDeviceLocked(), "Unlock the phone before clipboard testing. No clipboard changes were made.");
            String link = arguments.getString("pairLink"), url = arguments.getString("shareUrl");
            require(link != null && url != null, "pairLink and shareUrl are required.");
            require(PhoneStore.get(getTargetContext()).snapshot().getJSONArray("entries").length() == 0, "Use a fresh test installation.");
            createDisplay(720, 1440);
            result.putInt("displayId", displayId);
            main = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
            require(main.getDisplay().getDisplayId() == displayId, "Activity opened on the wrong display.");
            await(() -> main.hasWindowFocus() && !busy(), 10000, "Main activity did not become ready on the virtual display.");
            if (!cameraOnly) {
                clipboard = (ClipboardManager)main.getSystemService(Context.CLIPBOARD_SERVICE); onMain(() -> previous = clipboard.getPrimaryClip());
                click(main, "New collection"); input("Collection name", "Pocket"); click(main, "Create");
                click(main, "New collection"); input("Collection name", "Study"); click(main, "Create");
                clip(url); click(main, "Paste link"); click(main, "Phone · Pocket");
                await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 1 && "saved".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(0).optString("capture")), 15000, "Standalone phone image was not saved.");
                await(() -> hasImage(main.getWindow().getDecorView()), 5000, "Phone board did not decode its saved image.");
                clip(url); click(main, "Paste link"); click(main, "Phone · Pocket");
                require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 1, "Repeated phone paste duplicated a pin.");
                clip(url); click(main, "Paste link"); click(main, "Phone · Study");
                await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, 3000, "Same URL could not be saved in a different phone collection.");
                JSONObject local = MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(1);
                await(() -> "saved".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(1).optString("capture")), 15000, "Second phone preview was not saved.");
                await(() -> {
                    boolean[] opened = {false};
                    onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: " + local.optString("title")); if (card != null) opened[0] = card.performClick(); });
                    return opened[0];
                }, 5000, "Saved phone pin card did not become tappable.");
                pinActions(); click(main, "Edit pin"); input("Title", "Pocket reference"); input("Notes", "Saved without desktop"); click(main, "Save changes");
                input("Search this collection", "does not match"); require(text(main).contains("No matching pins."), "Phone collection search did not filter."); input("Search this collection", "Pocket reference"); require(text(main).contains("Pocket reference"), "Phone pin edit/search failed."); input("Search this collection", "");
                await(() -> hasImage(main.getWindow().getDecorView()), 5000, "Edited phone card did not decode its preview."); alignedBoard(); screenshot(main, "local.png");
                MobileLibrary.get(main).captured(local.getString("id"), new JSONObject().put("capture", "failed"));
                onMain(() -> { try { Method refresh = MainActivity.class.getDeclaredMethod("refreshLocal"); refresh.setAccessible(true); refresh.invoke(main); } catch (Exception error) { throw new RuntimeException(error); } });
                await(() -> hasImage(main.getWindow().getDecorView()) && text(main).contains("Preview unavailable"), 5000, "Failed retry hid its status behind cached media/notes."); alignedBoard();
                onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: Pocket reference"); require(captionText(card, "127.0.0.1") != null, "Failed retry hid the source website."); });
                MobileLibrary.get(main).captured(local.getString("id"), new JSONObject().put("capture", "saved"));
                Field singleton = MobileLibrary.class.getDeclaredField("instance"); singleton.setAccessible(true); singleton.set(null, null);
                require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2 && MobileLibrary.get(main).snapshot().toString().contains("Saved without desktop"), "Phone library did not survive a storage reopen.");
                require(PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0, "Local save entered desktop queue.");
            }
            if (!cameraOnly) { clip("Keep this " + url); click(main, "Paste link"); }
            else onMain(() -> main.onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Keep this " + url)));
            await(() -> has("Queue for desktop after pairing"), 5000, "Destination picker did not offer deferred desktop delivery.");
            click(main, "Queue for desktop after pairing");
            await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 1, 3000, "Paste did not queue the clipboard URL: " + ((TextView)field(main, "status")).getText());
            require(url.equals(PhoneStore.get(main).snapshot().getJSONArray("entries").getJSONObject(0).getString("url")), "Wrong clipboard URL.");
            if (!cameraOnly) click(main, "Paste link");
            else onMain(() -> main.onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)));
            await(() -> has("Queue for desktop after pairing"), 5000, "Repeated paste did not open destination picker."); click(main, "Queue for desktop after pairing");
            require(PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 1, "Repeated paste duplicated the pending URL.");
            screenshot(main, "unpaired.png");
            await(() -> !busy(), 3000, "Queue remained busy.");
            ActivityMonitor monitor = addMonitor(ScanActivity.class.getName(), null, false);
            click(main, "Connections");
            click(main, "Pair desktop");
            click(main, "Scan QR");
            scan = (ScanActivity)waitForMonitorWithTimeout(monitor, 10000); removeMonitor(monitor);
            require(scan != null && scan.getDisplay().getDisplayId() == displayId, "Pair desktop did not open the scanner on the isolated display.");
            await(() -> ((Long)field(scan, "lastFrame")) > 0 && text(scan).contains("Looking for Papan"), 10000, "The scanner did not receive live camera frames.");
            Method complete = ScanActivity.class.getDeclaredMethod("complete", String.class); complete.setAccessible(true);
            if (!cameraOnly) { clip("https://example.com/not-a-pairing-link"); click(scan, "Paste pairing link instead"); }
            else onMain(() -> { try { complete.invoke(scan, "https://example.com/not-a-pairing-link"); } catch (Exception error) { throw new RuntimeException(error); } });
            require(text(scan).contains("isn’t a Papan pairing link") && !scan.isFinishing(), "Invalid QR/paste closed the scanner.");
            BitMatrix qr = new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 480, 480);
            String decoded = null;
            for (int turn = 0; turn < 4; turn++) {
                byte[] pixels = new byte[480 * 480];
                for (int y = 0; y < 480; y++) for (int x = 0; x < 480; x++) {
                    int xx = turn == 0 ? x : turn == 1 ? y : turn == 2 ? 479 - x : 479 - y;
                    int yy = turn == 0 ? y : turn == 1 ? 479 - x : turn == 2 ? 479 - y : x;
                    pixels[y * 480 + x] = qr.get(xx, yy) ? 0 : (byte)255;
                }
                try { decoded = ScanActivity.decode(pixels, 480, 480); } catch (Exception error) { throw new AssertionError("Generated pairing QR did not decode at rotation " + turn, error); }
                require(link.equals(decoded), "QR failed at rotation " + turn);
            }
            final String pairing = decoded;
            onMain(() -> { try { complete.invoke(scan, pairing); } catch (Exception error) { throw new RuntimeException(error); } });
            await(() -> main.hasWindowFocus() && PhoneStore.get(main).snapshot().has("config"), 15000, "Decoded QR did not pair the desktop.");
            require(!((Boolean)field(main, "settingsOpen")), "Connection dialog did not close after pairing.");
            await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0 && ((TextView)field(main, "status")).getText().toString().contains("Saved in Papan."), 20000, "Foreground delivery did not observe the completed save.");
            screenshot(main, "paired.png");
            JSONObject before = PhoneStore.get(main).snapshot();
            if (!cameraOnly) { clip("no link here"); click(main, "Paste link"); require(((TextView)field(main, "status")).getText().toString().contains("No web link was found") && PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0, "Invalid clipboard contents queued a link."); }
            require(before.getJSONObject("config").toString().equals(PhoneStore.get(main).snapshot().getJSONObject("config").toString()), "Clipboard paste changed the pairing.");
            if (!cameraOnly) { nearbyPairing(); connectionFeedback(url, "connections-feedback.png"); }
            if (!cameraOnly && arguments.getString("controlEndpoint") != null) remoteBoards(url);
            if (!cameraOnly) { localVideo(url); collectionControls(url); responsive(); connectionFeedback(url, "connections-large-text.png"); }
            if (!cameraOnly && new File(getTargetContext().getFilesDir(), "style-demo.json").exists()) styleBoard();
            result.putString("checks", "Virtual-display UI; " + (cameraOnly ? "share intent queues and deduplicates (clipboard skipped); " : "native phone collection creation; local image capture/decoded preview; per-collection paste/deduplication; pin edit/search; private storage reopen; Paste link reads clipboard and chooses destination; ") + "Scan QR opens Camera2 and receives frames; invalid pairing stays in scanner; QR decoding at all four rotations; QR result pairs actual receiver; queued link saves and foreground status updates; pairing preserved; open desktop boards; selected destination retained across offline storage/activity restart; desktop pin order and manual rearrangement preserved across pagination, refresh and search; switching unlocked protected collections retains each password grant; protected password/cancel/wrong/correct access; protected video thumbnails decode from desktop; rotation preserves the unlocked collection and thumbnails in both orientations; backgrounding retains password access; an actual app close/reopen clears authorization; desktop lock clears contents and cached protected thumbnails.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Explore viewing: webpage video saves ahead of its poster and plays offline after the source refuses downloads; full-screen fitted local/remote videos autoplay with verified red decoded surface pixels; local video covers decode; play/pause and keyboard seeking work; vertical swipes move between pins and fetch more desktop pages; horizontal swipes change attachments without navigation buttons; pin overflow routes edit, copy link, open original, details, save to another collection, remove/cancel and retry; viewer rotation restores its reference, position and paused state; portrait desktop frame proportions remain aligned; a rebound remote thumbnail reuses the same decoded bitmap. Mobile previews fill tile bounds without stretching despite desktop whole-image fit; mismatched artwork proportions crop and the explicit whole-image preference survives reopening.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Native DNS-SD nearby desktop discovery; phone-initiated request and displayed six-digit code; desktop approval pairs without QR; authenticated automatic endpoint change preserves the pairing token, request ID and chosen destination; wrong-key identity proof is rejected.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Direct MP4 capture/native playback/deletion; collection rename/delete/cancel and form validation; add/share sheet cancellation; failed capture/retry preserves the link; source browser intent; pairing/address recovery controls; inline accessible Connections feedback, repeated pending-link warning and preserved destinations/key; no snackbar behind a modal, deferred board feedback after dismissal; keyboard focus; 300dp phone, 640dp tablet and 200% font reflow; native app-bar collection dropdown and one floating location chooser; location and Add share the bottom row with 48dp targets; collection/location menus open, fit and dismiss using keyboard; zero gaps between aligned pin rows and columns.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Collection dropdown highlights its selected row without checkboxes; the Phone/Desktop/Activity button shows the current window and its menu omits that window. Button/menu dimensions stay stable across locations; picker typography matches at every checked font scale; tapping the current button opens and dismisses without changing location.");
            if (!cameraOnly && new File(getTargetContext().getFilesDir(), "style-demo.json").exists()) result.putString("checks", result.getString("checks") + " Credited artwork board: equal card bounds and aligned rows/columns at three preview sizes; real slider keyboard input; collection-specific sizing persists across fresh activity; fill/whole-image control; light/dark/system appearance controls; native screenshots with decoded real artworks.");
            if (!cameraOnly) { result.putDouble("captionTitleContrast", captionTitleContrast); result.putDouble("captionSourceContrast", captionSourceContrast); result.putString("checks", result.getString("checks") + " Desktop-style captions: single-line title/source, no solid caption block, continuous rendered fade with AA contrast at every text row over pure white, full labels retained for details/accessibility."); }
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Preview rows reach both content edges without side padding.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Compact bottom controls retain 48dp tap targets with 40dp normal-size faces, stable scalable labels, dynamic scroll clearance and Add activation outside its visible face.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Real desktop-board swipes hide bottom controls on downward scrolling and restore them upward; refresh retains the hidden state and reference; reversal and keyboard recovery pass.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Native top bar quick-return reclaims its full height for previews; both bars retain their hidden state across refresh and restore through collection/Add keyboard focus.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Shared top/bottom progress stays synchronized at every sampled native frame during remote hide/show, with intermediate motion and no independent bottom animation.");
            result.putString("status", "passed");
        } catch (Throwable error) { result.putString("status", "failed"); result.putString("error", android.util.Log.getStackTraceString(error)); if (main != null) try { screenshot(main, "failure.png"); result.putString("screenStatus", ((TextView)field(main, "status")).getText().toString()); result.putString("visibleText", text(main)); result.putBoolean("paired", PhoneStore.get(main).snapshot().has("config")); } catch (Exception ignored) {} }
        finally {
            if (clipboard != null) onMain(() -> {
                ClipData current = clipboard.getPrimaryClip();
                if (current != null && current.getItemCount() > 0 && clipboardFixture != null && clipboardFixture.contentEquals(current.getItemAt(0).getText() == null ? "" : current.getItemAt(0).getText())) {
                    if (previous != null) clipboard.setPrimaryClip(previous); else clipboard.clearPrimaryClip();
                }
            });
            onMain(() -> { if (scan != null && !scan.isDestroyed()) scan.finish(); if (main != null && !main.isDestroyed()) main.finishAndRemoveTask(); });
            waitForIdleSync(); if (display != null) display.release(); if (output != null) output.close();
        }
        finish("passed".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void clip(String value) { clipboardFixture = value; onMain(() -> { require(!((KeyguardManager)getTargetContext().getSystemService(Context.KEYGUARD_SERVICE)).isDeviceLocked(), "Phone locked during clipboard testing."); clipboard.setPrimaryClip(ClipData.newPlainText("Papan test", value)); ClipData copy = clipboard.getPrimaryClip(); require(copy != null && value.contentEquals(copy.getItemAt(0).getText() == null ? "" : copy.getItemAt(0).getText()), "Test clipboard write/read did not match on this display."); }); }
    private void scrollOnly() {
        Bundle result = new Bundle();
        try {
            require("com.papan.share.test".equals(getTargetContext().getPackageName()), "Only the disposable test package is allowed.");
            createDisplay(720, 1440); result.putInt("displayId", displayId);
            main = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
            await(() -> main.hasWindowFocus() && !busy(), 10000, "Virtual scroll board did not initialize.");
            MobileLibrary store = MobileLibrary.get(main); String collection = store.create("Scroll test fixtures"), empty = store.create("Empty scroll fixture"), shortBoard = store.create("Short scroll fixture");
            String single = store.add(shortBoard, "https://example.invalid/short-scroll-fixture"); store.captured(single, new JSONObject().put("title", "Short scroll fixture").put("kind", "link").put("capture", "saved"));
            for (int i = 0; i < 40; i++) { String id = store.add(collection, "https://example.invalid/scroll-fixture/" + i); store.captured(id, new JSONObject().put("title", "Scroll fixture " + i).put("kind", "link").put("capture", "saved")); }
            onMain(() -> { try { Field selected = MainActivity.class.getDeclaredField("selectedLocal"); selected.setAccessible(true); selected.set(main, collection); Method refresh = MainActivity.class.getDeclaredMethod("refreshLocal"); refresh.setAccessible(true); refresh.invoke(main); } catch (Exception error) { throw new RuntimeException(error); } });
            await(() -> grid().getItemCount() == 40, 5000, "Scroll fixture did not enter the native board."); scrollingControls(true);
            relaunch(600, 1400, 1); scrollingControls(false); relaunch(1280, 1600, 1); scrollingControls(false); relaunch(720, 1600, 2); scrollingControls(false);
            swipeBoard(true); await(() -> ((View)field(main, "actions")).getTranslationY() > 0, 5000, "Controls did not hide before changing collection.");
            onMain(() -> { try { Field selected = MainActivity.class.getDeclaredField("selectedLocal"); selected.setAccessible(true); selected.set(main, empty); Method refresh = MainActivity.class.getDeclaredMethod("refreshLocal"); refresh.setAccessible(true); refresh.invoke(main); } catch (Exception error) { throw new RuntimeException(error); } });
            await(() -> main.hasWindowFocus() && has("This collection is empty.") && ((View)field(main, "appBar")).getTop() == 0, 5000, "New empty collection did not finish opening."); require(((View)field(main, "actions")).getTranslationY() == 0, "New empty collection left navigation hidden.");
            click(main, "Short scroll fixture"); await(() -> grid().getItemCount() == 1 && ((View)field(main, "appBar")).getTop() == 0, 5000, "Short fixture did not open."); swipeBoard(true);
            require(((View)field(main, "appBar")).getTop() == 0 && ((View)field(main, "actions")).getTranslationY() == 0, "A short non-scrolling board hid navigation.");
            result.putString("checks", "Real private-display swipes hide both bottom controls when scrolling down and restore them when scrolling up. The native top bar hides and quick-returns too; the actual preview viewport gains exactly its height with no blank gap. Native frame samples and actual compositor pixels show shared top/bottom progress with intermediate hide/show motion. Same-board refresh retains both hidden bars and the visible reference. Rapid direction reversal settles correctly; keyboard focus reveals controls and Tab/Enter opens Add and the collection dropdown. Fresh/empty collections restore controls; short boards keep navigation visible. Compact sizes, 48dp targets and clearance pass at 300dp/360dp/640dp and 200% text. No physical phone used."); result.putString("status", "passed");
        } catch (Throwable error) { result.putString("status", "failed"); result.putString("error", android.util.Log.getStackTraceString(error)); try { View actions = (View)field(main, "actions"); RecyclerView board = (RecyclerView)field(main, "pinGrid"); result.putString("scrollState", "width=" + main.getResources().getConfiguration().screenWidthDp + ",font=" + testFontScale + ",translation=" + actions.getTranslationY() + ",height=" + actions.getHeight() + ",focus=" + actions.hasFocus() + ",offset=" + board.computeVerticalScrollOffset() + ",up=" + board.canScrollVertically(-1) + ",down=" + board.canScrollVertically(1)); } catch (Exception ignored) {} }
        finally { onMain(() -> { if (main != null && !main.isDestroyed()) main.finishAndRemoveTask(); }); waitForIdleSync(); if (display != null) display.release(); if (output != null) output.close(); }
        finish("passed".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void swipeBoard(boolean down) throws Exception { swipeBoard(down, 450); }
    private void swipeBoard(boolean down, int duration) throws Exception {
        require(displayId != Display.DEFAULT_DISPLAY && main.getDisplay().getDisplayId() == displayId, "Refusing a swipe outside the isolated display."); int[] point = new int[2]; int[] size = new int[2];
        getUiAutomation().waitForIdle(200, 3000);
        onMain(() -> { try { View board = (View)field(main, "pinGrid"); android.graphics.Rect visible = new android.graphics.Rect(); require(board.getGlobalVisibleRect(visible), "Board is outside the virtual viewport."); board.getRootView().getLocationOnScreen(point); point[0] += visible.left; point[1] += visible.top; size[0] = visible.width(); size[1] = visible.height(); } catch (Exception error) { throw new RuntimeException(error); } });
        int x = point[0] + size[0] / 2, upper = point[1] + size[1] / 4, lower = point[1] + size[1] * 3 / 4;
        try (android.os.ParcelFileDescriptor.AutoCloseInputStream result = new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("input -d " + displayId + " swipe " + x + " " + (down ? lower : upper) + " " + x + " " + (down ? upper : lower) + " " + duration))) { require(result.read() == -1, "Virtual board swipe failed."); }
        onMain(() -> { try { ((RecyclerView)field(main, "pinGrid")).stopScroll(); } catch (Exception error) { throw new RuntimeException(error); } });
    }
    private void scrollingControls(boolean capture) throws Exception {
        if (((View)field(main, "searchLayout")).isShown()) { click(main, "Search"); await(() -> !busy(), 5000, "Search did not finish closing before scrolling."); }
        await(() -> ((View)field(main, "pinGrid")).canScrollVertically(1), 5000, "Native board is too short to exercise scrolling."); compactControls();
        View actions = (View)field(main, "actions"), header = (View)field(main, "appBar"); RecyclerView board = (RecyclerView)field(main, "pinGrid"); android.graphics.Rect shown = new android.graphics.Rect();
        await(() -> header.getTop() == 0, 5000, "Top bar did not initialize expanded."); onMain(() -> require(board.getGlobalVisibleRect(shown), "Expanded board is outside the viewport."));
        if (capture) screenshot(main, "scroll-controls-before.png");
        sharedBarSwipe(true, capture); await(() -> actions.getTranslationY() >= actions.getHeight() && header.getBottom() <= 0 && board.canScrollVertically(-1), 5000, "Scrolling down did not clear the top and bottom controls.");
        onMain(() -> { try { android.graphics.Rect visible = new android.graphics.Rect(); require(!((View)field(main, "locationButton")).getGlobalVisibleRect(visible) && !((View)field(main, "addLink")).getGlobalVisibleRect(visible), "Hidden controls still cover the preview viewport."); } catch (Exception error) { throw new RuntimeException(error); } });
        onMain(() -> { try { android.graphics.Rect visible = new android.graphics.Rect(); require(!((View)field(main, "toolbar")).getGlobalVisibleRect(visible), "Top bar still covers previews."); require(board.getGlobalVisibleRect(visible) && visible.height() - shown.height() == header.getHeight() && shown.top - visible.top == header.getHeight(), "Hiding the top bar left a gap instead of reclaiming its height."); } catch (Exception error) { throw new RuntimeException(error); } });
        if (capture) screenshot(main, "scroll-controls-hidden.png");
        int first = grid().findFirstVisibleItemPosition(), top = grid().getDecoratedTop(grid().findViewByPosition(first));
        onMain(() -> { try { Method refresh = MainActivity.class.getDeclaredMethod("renderPins"); refresh.setAccessible(true); refresh.invoke(main); } catch (Exception error) { throw new RuntimeException(error); } }); waitForIdleSync();
        await(() -> grid().findFirstVisibleItemPosition() == first, 5000, "Refresh changed the visible reference."); require(actions.getTranslationY() >= actions.getHeight() && header.getBottom() <= 0 && grid().getDecoratedTop(grid().findViewByPosition(first)) == top, "Refresh revealed controls or moved the board.");
        sharedBarSwipe(false, capture); await(() -> actions.getTranslationY() == 0 && header.getTop() == 0, 5000, "Scrolling up did not restore top and bottom controls.");
        if (capture) screenshot(main, "scroll-controls-returned.png");
        swipeBoard(true); swipeBoard(false); await(() -> actions.getTranslationY() == 0 && header.getTop() == 0, 5000, "Direction reversal left controls hidden.");
        swipeBoard(true); await(() -> header.getBottom() <= 0, 5000, "Top bar did not hide before keyboard recovery."); key(android.view.KeyEvent.KEYCODE_TAB);
        onMain(() -> { try { require(((View)field(main, "collectionButton")).requestFocus(), "Collection picker could not receive keyboard focus."); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> header.getTop() == 0 && actions.getTranslationY() == 0, 5000, "Keyboard focus did not restore the top bar."); key(android.view.KeyEvent.KEYCODE_ENTER); await(() -> ((androidx.appcompat.widget.ListPopupWindow)field(main, "collectionMenu")).isShowing(), 5000, "Keyboard-revealed collection picker did not open."); key(android.view.KeyEvent.KEYCODE_ESCAPE); await(main::hasWindowFocus, 5000, "Collection menu did not dismiss.");
        swipeBoard(true); await(() -> actions.getTranslationY() >= actions.getHeight(), 5000, "Controls did not hide before keyboard recovery.");
        key(android.view.KeyEvent.KEYCODE_TAB);
        onMain(() -> { try { require(((View)field(main, "locationButton")).requestFocus(), "Location could not receive keyboard focus."); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> actions.getTranslationY() == 0 && header.getTop() == 0, 5000, "Keyboard focus did not reveal the controls."); key(android.view.KeyEvent.KEYCODE_TAB); require(((View)field(main, "addLink")).hasFocus(), "Tab did not reach Add after keyboard recovery."); key(android.view.KeyEvent.KEYCODE_ENTER); await(() -> has("Add a link"), 5000, "Keyboard-revealed Add could not open its dialog."); click(main, "Cancel");
        onMain(() -> { try { ((View)field(main, "locationButton")).clearFocus(); ((View)field(main, "addLink")).clearFocus(); board.scrollToPosition(0); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> grid().findFirstVisibleItemPosition() == 0, 5000, "Scroll check did not restore the first row.");
    }
    private int wordmarkBottom(Bitmap frame, int color) {
        for (int y = Math.min(frame.getHeight(), barPixels[2]) - 1; y >= 0; y--) for (int x = barPixels[0]; x < barPixels[1]; x += 2) if (colorDifference(frame.getPixel(x, y), color) <= 1) return y;
        return -1;
    }
    private int locationTop(Bitmap frame, int color) {
        for (int y = Math.max(0, barPixels[5] - 2); y < frame.getHeight(); y++) if (colorDifference(frame.getPixel(barPixels[3], y), color) <= 1) return y;
        return -1;
    }
    private void sharedBarSwipe(boolean down, boolean pixels) throws Exception {
        require(displayId != Display.DEFAULT_DISPLAY && main.getDisplay().getDisplayId() == displayId, "Motion checks require the private display.");
        com.google.android.material.appbar.AppBarLayout header = (com.google.android.material.appbar.AppBarLayout)field(main, "appBar"); View actions = (View)field(main, "actions");
        java.util.List<double[]> samples = java.util.Collections.synchronizedList(new java.util.ArrayList<>()); java.util.List<Bitmap> frames = java.util.Collections.synchronizedList(new java.util.ArrayList<>()); boolean[] recording = {true};
        android.view.Choreographer.FrameCallback sampler = new android.view.Choreographer.FrameCallback() { @Override public void doFrame(long time) { if (!recording[0]) return; samples.add(new double[] {-(double)header.getTop() / header.getTotalScrollRange(), actions.getTranslationY() / actions.getHeight()}); android.view.Choreographer.getInstance().postFrameCallback(this); } };
        try {
            onMain(() -> { android.view.Choreographer.getInstance().postFrameCallback(sampler); if (pixels) { output.setOnImageAvailableListener(reader -> { try (Image frame = reader.acquireLatestImage()) { if (frame != null && frames.size() < 18) frames.add(displayFrame(frame)); } }, new Handler(Looper.getMainLooper())); main.getWindow().getDecorView().invalidate(); } });
            if (pixels) {
                await(() -> !frames.isEmpty(), 5000, "Compositor did not produce a motion baseline.");
                if (down) onMain(() -> { try { View brand = description(main.getWindow().getDecorView(), "Papan"), location = (View)field(main, "locationButton"); int[] point = new int[2]; brand.getLocationOnScreen(point); barPixels[0] = point[0]; barPixels[1] = point[0] + brand.getWidth(); barPixels[2] = header.getBottom(); location.getLocationOnScreen(point); barPixels[3] = point[0] + location.getWidth() / 2; Bitmap before = frames.get(frames.size() - 1); barPixels[4] = wordmarkBottom(before, main.getColor(R.color.papan_on_surface)); barPixels[5] = locationTop(before, main.getColor(R.color.papan_primary_container)); require(barPixels[4] >= 0 && barPixels[5] >= 0, "Native bar pixels were not identifiable."); } catch (Exception error) { throw new RuntimeException(error); } });
                onMain(() -> { for (Bitmap frame : new java.util.ArrayList<>(frames)) frame.recycle(); frames.clear(); });
            }
            swipeBoard(down, 900); await(() -> down ? header.getBottom() <= 0 : header.getTop() == 0, 5000, "Native bar transition did not settle.");
            onMain(() -> { recording[0] = false; android.view.Choreographer.getInstance().removeFrameCallback(sampler); if (pixels) output.setOnImageAvailableListener(reader -> { try (Image frame = reader.acquireLatestImage()) {} }, new Handler(Looper.getMainLooper())); });
            double error = 0; int intermediate = 0; org.json.JSONArray steps = new org.json.JSONArray();
            for (double[] sample : new java.util.ArrayList<>(samples)) { error = Math.max(error, Math.abs(sample[0] - sample[1])); if (sample[0] > .02 && sample[0] < .98) intermediate++; steps.put(new org.json.JSONArray().put(sample[0]).put(sample[1])); }
            int paired = 0, first = -1, last = -1; double pixelError = 0;
            for (int n = 0; n < frames.size(); n++) { Bitmap frame = frames.get(n); int glyph = wordmarkBottom(frame, main.getColor(R.color.papan_on_surface)), green = locationTop(frame, main.getColor(R.color.papan_primary_container)); if (glyph < 0 || green < 0) continue; double top = (double)(barPixels[4] - glyph) / header.getTotalScrollRange(), bottom = (double)(green - barPixels[5]) / actions.getHeight(); if (top <= .02 || top >= .8) continue; paired++; pixelError = Math.max(pixelError, Math.abs(top - bottom)); if (first < 0) first = n; last = n; }
            boolean passed = intermediate >= 2 && error < .01 && (!pixels || paired >= 2 && pixelError < .025);
            if (pixels) {
                String direction = down ? "hide" : "show"; JSONObject proof = new JSONObject().put("status", passed ? "passed" : "failed").put("displayId", displayId).put("compositorFrames", frames.size()).put("pairedMovingFrames", paired).put("nativeSamples", samples.size()).put("intermediateSamples", intermediate).put("maxNativePhaseDifference", error).put("maxPixelPhaseDifference", pixelError).put("progress", steps);
                try (FileOutputStream file = new FileOutputStream(new File(main.getFilesDir(), "bar-motion-" + direction + ".json"))) { file.write(proof.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                if (first >= 0) for (String name : new String[] {"first", "last"}) try (FileOutputStream file = new FileOutputStream(new File(main.getFilesDir(), "bar-motion-" + direction + "-" + name + ".png"))) { frames.get(name.equals("first") ? first : last).compress(Bitmap.CompressFormat.PNG, 100, file); }
            }
            require(passed, "Bars staggered or jumped during " + (down ? "hide" : "show") + ": native phase difference=" + error + ", pixel phase difference=" + pixelError + ", intermediate frames=" + intermediate + ", paired compositor frames=" + paired);
        } finally { onMain(() -> { recording[0] = false; android.view.Choreographer.getInstance().removeFrameCallback(sampler); if (pixels) output.setOnImageAvailableListener(reader -> { try (Image frame = reader.acquireLatestImage()) {} }, new Handler(Looper.getMainLooper())); }); for (Bitmap frame : new java.util.ArrayList<>(frames)) if (!frame.isRecycled()) frame.recycle(); }
    }
    private void locationOnly() {
        Bundle result = new Bundle();
        try {
            require("com.papan.share.test".equals(getTargetContext().getPackageName()), "Only the disposable test package is allowed.");
            createDisplay(720, 1440); result.putInt("displayId", displayId);
            main = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeCustomAnimation(getTargetContext(), 0, 0).setLaunchDisplayId(displayId).toBundle());
            await(() -> main.hasWindowFocus() && !busy(), 10000, "Virtual location board did not initialize."); locationMotion(); locationSizing();
            relaunch(600, 1400, 1); locationSizing(); relaunch(1280, 1600, 1); locationSizing(); relaunch(720, 1600, 2); locationSizing(); locationScrolling();
            result.putString("checks", "Composited virtual-display motion frames keep the current button uncovered while the menu reveals upward; native location selection, stable dimensions/typography and current-button dismissal pass at 300dp/360dp/640dp and 200% text. All location labels stay on one line without ellipsis; a short 210dp display scrolls the native menu to its second destination. Compact controls retain 48dp tap targets, 40dp normal-size faces, scalable labels and dynamic clearance; Add activates outside its visible face. No physical phone used."); result.putString("status", "passed");
        } catch (Throwable error) { result.putString("status", "failed"); result.putString("error", android.util.Log.getStackTraceString(error)); }
        finally { onMain(() -> { if (main != null && !main.isDestroyed()) main.finishAndRemoveTask(); }); waitForIdleSync(); if (display != null) display.release(); if (output != null) output.close(); }
        finish("passed".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void viewerMenuOnly() {
        Bundle result = new Bundle();
        try {
            require("com.papan.share.test".equals(getTargetContext().getPackageName()), "Use the disposable test package."); createDisplay(720, 1440); result.putInt("displayId", displayId);
            MobileLibrary store = MobileLibrary.get(getTargetContext()); String collection = store.create("Menu check"), id = store.add(collection, "https://example.com/menu-check"); store.captured(id, new JSONObject().put("title", "Menu reference").put("capture", "saved"));
            main = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
            await(() -> main.hasWindowFocus(), 5000, "Menu board did not initialize."); clipboard = (ClipboardManager)main.getSystemService(Context.CLIPBOARD_SERVICE);
            onMain(() -> description(main.getWindow().getDecorView(), "Open pin: Menu reference").performClick());
            for (int i = 0; i < 3; i++) {
                pinActions(); click(main, "Copy link");
                onMain(() -> { try { PinViewer viewer = (PinViewer)field(main, "detailDialog"); require(viewer.isShowing() && viewer.getWindow().getDecorView().hasWindowFocus(), "Copy did not dismiss the native popup and return focus to the viewer."); } catch (Exception error) { throw new RuntimeException(error); } });
                click(main, "Details"); await(() -> has("Pin details"), 3000, "Details sheet did not open after copying."); click(main, "Close");
            }
            click(main, "Save to another collection"); await(() -> has("Save link"), 3000, "Save sheet did not open after closing the viewer."); click(main, "Cancel"); await(main::hasWindowFocus, 3000, "Save cancellation did not return to the board.");
            onMain(() -> description(main.getWindow().getDecorView(), "Open pin: Menu reference").performClick()); click(main, "Remove pin"); click(main, "Keep"); click(main, "Close reference"); result.putString("checks", "Three consecutive copy/details/close sequences wait for native popup removal and retain viewer focus; save-to-collection/cancel, remove cancellation and close work on a private display."); result.putString("status", "passed");
        } catch (Throwable error) { result.putString("status", "failed"); result.putString("error", android.util.Log.getStackTraceString(error)); }
        finally { onMain(() -> { if (main != null && !main.isDestroyed()) main.finishAndRemoveTask(); }); waitForIdleSync(); if (display != null) display.release(); if (output != null) output.close(); }
        finish("passed".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private Bitmap displayFrame(Image frame) {
        Image.Plane plane = frame.getPlanes()[0]; int width = plane.getRowStride() / plane.getPixelStride(); Bitmap padded = Bitmap.createBitmap(width, frame.getHeight(), Bitmap.Config.ARGB_8888); java.nio.ByteBuffer pixels = plane.getBuffer(); pixels.rewind(); padded.copyPixelsFromBuffer(pixels); Bitmap bitmap = Bitmap.createBitmap(padded, 0, 0, frame.getWidth(), frame.getHeight()); if (bitmap != padded) padded.recycle(); return bitmap;
    }
    private void locationMotion() throws Exception {
        java.util.List<Bitmap> frames = java.util.Collections.synchronizedList(new java.util.ArrayList<>()); int[] button = new int[4];
        onMain(() -> { try { View anchor = (View)field(main, "locationButton"); int[] point = new int[2]; anchor.getLocationOnScreen(point); button[0] = point[0]; button[1] = point[1]; button[2] = point[0] + anchor.getWidth(); button[3] = point[1] + anchor.getHeight(); output.setOnImageAvailableListener(reader -> { try (Image frame = reader.acquireLatestImage()) { if (frame != null && frames.size() < 18) frames.add(displayFrame(frame)); } }, new Handler(Looper.getMainLooper())); main.getWindow().getDecorView().invalidate(); } catch (Exception error) { throw new RuntimeException(error); } });
        try {
            await(() -> !frames.isEmpty(), 5000, "Virtual display did not render a baseline frame."); Bitmap before = frames.get(frames.size() - 1); onMain(() -> { for (Bitmap frame : new java.util.ArrayList<>(frames)) if (frame != before) frame.recycle(); frames.clear(); });
            onMain(() -> { try { ((View)field(main, "locationButton")).performClick(); } catch (Exception error) { throw new RuntimeException(error); } });
            await(() -> !main.hasWindowFocus(), 5000, "Location popup did not open."); popupFits("location-motion-menu.png");
            java.util.List<Bitmap> captured = new java.util.ArrayList<>(); onMain(() -> { output.setOnImageAvailableListener(reader -> { try (Image frame = reader.acquireLatestImage()) {} }, new Handler(Looper.getMainLooper())); captured.addAll(frames); });
            require(captured.size() >= 2, "Menu had no observable native animation frames."); int first = -1, last = -1, firstTop = Integer.MAX_VALUE, lastTop = Integer.MAX_VALUE;
            for (int n = 0; n < captured.size(); n++) {
                Bitmap frame = captured.get(n); int inset = (button[3] - button[1]) / 2;
                for (int y = button[1] + 2; y < button[3] - 2; y += 4) for (int x = button[0] + inset; x < button[2] - inset; x += 4) require(colorDifference(before.getPixel(x, y), frame.getPixel(x, y)) <= 12, "Opening menu painted over the current-location button at frame " + n);
                int top = Integer.MAX_VALUE, changed = 0;
                for (int y = 0; y < button[1] - 4; y += 2) for (int x = Math.max(0, button[0] - 16); x < Math.min(frame.getWidth(), button[2] + 16); x += 2) if (colorDifference(before.getPixel(x, y), frame.getPixel(x, y)) > 24) { top = Math.min(top, y); changed++; }
                if (changed > 12) { if (first < 0) { first = n; firstTop = top; } last = n; lastTop = top; }
            }
            require(first >= 0 && last > first && firstTop > lastTop + 16, "Menu did not visibly emerge upward from behind its button: " + firstTop + " -> " + lastTop);
            androidx.appcompat.widget.ListPopupWindow menu = (androidx.appcompat.widget.ListPopupWindow)field(main, "locationMenu"); android.widget.PopupWindow nativePopup = (android.widget.PopupWindow)field(menu, "mPopup");
            JSONObject proof = new JSONObject().put("status", "passed").put("displayId", displayId).put("frames", captured.size()).put("firstVisibleTop", firstTop).put("finalVisibleTop", lastTop).put("buttonUncovered", true).put("nativeTransition", nativePopup.getEnterTransition() == null ? "none" : nativePopup.getEnterTransition().getClass().getName());
            try (FileOutputStream file = new FileOutputStream(new File(main.getFilesDir(), "menu-motion.json"))) { file.write(proof.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            for (String name : new String[] {"before", "first", "last"}) { Bitmap frame = name.equals("before") ? before : captured.get(name.equals("first") ? first : last); try (FileOutputStream file = new FileOutputStream(new File(main.getFilesDir(), "menu-motion-" + name + ".png"))) { frame.compress(Bitmap.CompressFormat.PNG, 100, file); } }
            before.recycle(); for (Bitmap frame : captured) frame.recycle(); tapLocationButton(); await(main::hasWindowFocus, 5000, "Current location button did not dismiss the revealed menu.");
        } finally { onMain(() -> output.setOnImageAvailableListener(reader -> { try (Image frame = reader.acquireLatestImage()) {} }, new Handler(Looper.getMainLooper()))); for (Bitmap frame : new java.util.ArrayList<>(frames)) if (!frame.isRecycled()) frame.recycle(); }
    }
    private int colorDifference(int a, int b) { return Math.max(Math.max(Math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)), Math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b))), Math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))); }
    private void click(Activity activity, String label) {
        require(activity.getDisplay().getDisplayId() == displayId, "Refusing interaction outside the test display.");
        if (activity == main && (label.equals("Phone") || label.equals("Desktop") || label.equals("Activity"))) try {
            TextView location = (TextView)field(main, "locationButton");
            if (label.contentEquals(location.getText())) {
                await(main::hasWindowFocus, 5000, "Current location button is covered."); onMain(location::performClick);
                await(() -> !main.hasWindowFocus(), 5000, "Current location button did not open its menu.");
                require(!has(label), "Current location is repeated in its dropdown."); tapLocationButton(); await(main::hasWindowFocus, 5000, "Current location button did not dismiss its menu."); require(label.contentEquals(location.getText()), "Tapping the current button changed location."); return;
            }
        } catch (Exception error) { throw new RuntimeException(error); }
        boolean picker = activity == main && (label.equals("Phone") || label.equals("Desktop") || label.equals("Activity") || collectionAvailable(label));
        if (picker) try { await(() -> { boolean[] ready = new boolean[1]; onMain(() -> ready[0] = main.hasWindowFocus() || findPopup(label) != null); return ready[0]; }, 5000, "Picker is still transitioning: " + label); } catch (Exception error) { throw new RuntimeException(error); }
        if (picker) onMain(() -> { if (!main.hasWindowFocus()) return; try {
            if (label.equals("Phone") || label.equals("Desktop") || label.equals("Activity")) ((View)field(main, "locationButton")).performClick();
            else if (collectionAvailable(label)) ((View)field(main, "collectionButton")).performClick();
        } catch (Exception error) { throw new RuntimeException(error); } });
        if (label.equals("Load more pins")) onMain(() -> { try { RecyclerView grid = (RecyclerView)field(main, "pinGrid"); grid.scrollToPosition(grid.getAdapter().getItemCount() - 1); } catch (Exception error) { throw new RuntimeException(error); } });
        try { await(() -> has(label), 1000, "Control still transitioning."); } catch (Exception | AssertionError ignored) {}
        if (!has(label) && activity == main) onMain(() -> { try { PinViewer viewer = (PinViewer)field(main, "detailDialog"); com.google.android.material.appbar.MaterialToolbar toolbar = (com.google.android.material.appbar.MaterialToolbar)field(viewer != null && viewer.isShowing() ? viewer : main, "toolbar"); for (int i = 0; i < toolbar.getMenu().size(); i++) { android.view.MenuItem item = toolbar.getMenu().getItem(i); if (item.isVisible() && label.contentEquals(item.getTitle())) { toolbar.showOverflowMenu(); break; } } } catch (Exception error) { throw new RuntimeException(error); } });
        boolean[] clicked = new boolean[1]; View[] selectedPopup = new View[1];
        try { await(() -> { onMain(() -> {
            View view = picker ? findPopup(label) : findWindow(label); if (view == null || !view.isShown()) return;
            if (picker || pickerList(view.getRootView()) != null) selectedPopup[0] = view.getRootView();
            require(view.getDisplay().getDisplayId() == displayId, "Wrong window display.");
            view.requestRectangleOnScreen(new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight()));
            while (!view.isClickable() && view.getParent() instanceof View) {
                if (view.getParent() instanceof android.widget.AdapterView) { android.widget.AdapterView<?> list = (android.widget.AdapterView<?>)view.getParent(); int position = list.getPositionForView(view); require(position >= 0, "Menu item has no position."); list.performItemClick(view, position, list.getItemIdAtPosition(position)); clicked[0] = true; return; }
                view = (View)view.getParent();
            }
            require(view.isClickable(), "Control is not clickable: " + label); view.performClick(); clicked[0] = true;
        }); return clicked[0]; }, 5000, "Missing focused control: " + label); } catch (Exception error) { throw new RuntimeException(error); }
        if (selectedPopup[0] != null) try { await(() -> { boolean[] dismissed = new boolean[1]; onMain(() -> dismissed[0] = !WindowInspector.getGlobalWindowViews().contains(selectedPopup[0])); return dismissed[0]; }, 5000, "Selected menu did not dismiss: " + label + " attached=" + selectedPopup[0].isAttachedToWindow() + " shown=" + selectedPopup[0].isShown() + " focus=" + selectedPopup[0].hasWindowFocus()); } catch (Exception error) { throw new RuntimeException(error); }
    }
    private View findWindow(String label) { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { View found = find(window, label); if (found != null && found.isShown()) return found; } return null; }
    private View findPopup(String label) { for (View window : WindowInspector.getGlobalWindowViews()) if (window != main.getWindow().getDecorView() && window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { View found = find(window, label); if (found != null && found.isShown()) return found; } return null; }
    private boolean has(String label) { boolean[] result = new boolean[1]; onMain(() -> result[0] = findWindow(label) != null); return result[0]; }
    private boolean collectionAvailable(String label) { try { org.json.JSONArray choices = (org.json.JSONArray)field(main, "collectionChoices"); for (int i = 0; i < choices.length(); i++) { JSONObject choice = choices.getJSONObject(i); if (label.equals(choice.optString("name") + (choice.optBoolean("protected") ? " · protected" : ""))) return true; } return false; } catch (Exception error) { throw new RuntimeException(error); } }
    private void input(String hint, String value) { if (hint.equals("Search this collection")) { try { if (!((View)field(main, "searchLayout")).isShown()) click(main, "Search"); } catch (Exception error) { throw new RuntimeException(error); } } try { await(() -> { boolean[] found = new boolean[1]; onMain(() -> { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId && inputView(window, hint) != null) found[0] = true; }); return found[0]; }, 5000, "Missing focused input: " + hint); } catch (Exception error) { throw new RuntimeException(error); } onMain(() -> { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { EditText input = inputView(window, hint); if (input != null) { input.setText(value); return; } } throw new AssertionError("Missing input: " + hint); }); }
    private EditText inputView(View root, String hint) { if (root instanceof EditText && hint.contentEquals(((EditText)root).getHint())) return (EditText)root; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { EditText found = inputView(((ViewGroup)root).getChildAt(i), hint); if (found != null) return found; } return null; }
    private View description(View root, String label) { if (label.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { View found = description(((ViewGroup)root).getChildAt(i), label); if (found != null) return found; } return null; }
    private boolean hasImage(View root) { if (root instanceof ImageView && root.getTag() instanceof TextView && ((ImageView)root).getDrawable() instanceof android.graphics.drawable.BitmapDrawable) return true; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) if (hasImage(((ViewGroup)root).getChildAt(i))) return true; return false; }
    private Bitmap imageBitmap(View root) { if (root instanceof ImageView && root.getTag() instanceof TextView && ((ImageView)root).getDrawable() instanceof android.graphics.drawable.BitmapDrawable) return ((android.graphics.drawable.BitmapDrawable)((ImageView)root).getDrawable()).getBitmap(); if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { Bitmap bitmap = imageBitmap(((ViewGroup)root).getChildAt(i)); if (bitmap != null) return bitmap; } return null; }
    private void localVideo(String imageUrl) throws Exception {
        String url = arguments.getString("videoPageUrl"); clip(url); click(main, "Paste link"); click(main, "Phone · Pocket");
        await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 3 && "saved".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2).optString("capture")), 15000, "Webpage video was not captured on phone.");
        JSONObject pin = MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2); File media = MobileLibrary.get(main).mediaFile(pin.getString("mediaFile")); require(media.isFile() && "video".equals(pin.optString("kind")), "Page saved a poster instead of local video bytes."); control("disable-phone-video");
        await(() -> has("Open pin: Saved phone video"), 5000, "Captured video did not appear on the phone board.");
        await(() -> imageBitmap(description(main.getWindow().getDecorView(), "Open pin: Saved phone video")) != null, 10000, "Local video did not get a decoded frame cover."); alignedBoard(); screenshot(main, "video-board.png");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: Saved phone video"); require(card != null, "Video pin missing."); card.performClick(); });
        await(() -> viewerVideo() != null && viewerVideo().getCurrentPosition() > 200, 10000, "Saved phone video did not autoplay in the viewer.");
        PinViewer viewer = (PinViewer)field(main, "detailDialog"); fittedVideo(viewer); screenshot(main, viewer.getWindow(), "video-viewer.png");
        click(main, "Pause"); int paused = viewerVideo().getCurrentPosition(); SystemClock.sleep(300); require(!viewerVideo().isPlaying() && Math.abs(viewerVideo().getCurrentPosition() - paused) < 100, "Pause did not stop playback.");
        onMain(() -> { try { com.google.android.material.slider.Slider slider = (com.google.android.material.slider.Slider)field(viewer, "seek"); slider.requestFocus(); slider.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)); slider.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> viewerVideo().getCurrentPosition() > paused + 50, 5000, "Native video seek control did not seek.");
        click(main, "Play"); await(() -> viewerVideo().isPlaying(), 5000, "Play did not resume playback.");
        swipeViewer(viewer, 0, -220); await(() -> field(main, "detailDialog") != viewer && viewerVideo() == null, 5000, "Swipe up did not move to the next pin.");
        swipeViewer((PinViewer)field(main, "detailDialog"), 0, -220); await(() -> has("End of this collection."), 3000, "Collection boundary feedback was hidden behind the viewer.");
        swipeViewer((PinViewer)field(main, "detailDialog"), 0, 220); await(() -> viewerVideo() != null && viewerVideo().isPlaying(), 10000, "Swipe down did not return to the video.");
        pinActions(); click(main, "Copy link"); require(url.equals(clipboard.getPrimaryClip().getItemAt(0).getText().toString()), "Copy link did not copy the original webpage URL.");
        click(main, "Details"); await(() -> has("Pin details"), 3000, "Details sheet did not receive focus."); require(!has("Edit pin") && !has("Open original"), "Details still contains pin actions."); click(main, "Close");
        click(main, "Remove pin"); click(main, "Keep"); require(media.isFile(), "Remove cancellation deleted the video.");
        click(main, "Remove pin"); click(main, "Remove"); await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2 && !media.exists(), 5000, "Phone remove did not remove saved media.");
    }
    private void pinActions() throws Exception {
        PinViewer viewer = (PinViewer)field(main, "detailDialog");
        await(() -> viewer != null && viewer.isShowing() && viewer.getWindow().getDecorView().hasWindowFocus(), 3000, "Pin viewer did not regain focus before reopening its menu.");
        require(viewer != null && !has("Details") && !has("Next") && !has("Previous"), "Viewer exposes pin actions outside its overflow.");
        click(main, "More options"); await(() -> has("Copy link") && has("Open original") && has("Details"), 3000, "Pin actions are missing from overflow.");
        require(!has("Previous pin") && !has("Next pin"), "Pin navigation still occupies the menu.");
        waitForIdleSync(); onMain(() -> {
            View popup = findWindow("Copy link").getRootView(); require(popup != viewer.getWindow().getDecorView(), "Pin actions are not in a separate native popup.");
            Bitmap bitmap = Bitmap.createBitmap(popup.getWidth(), popup.getHeight(), Bitmap.Config.ARGB_8888); popup.draw(new android.graphics.Canvas(bitmap));
            try (FileOutputStream file = new FileOutputStream(new File(main.getFilesDir(), "pin-actions.png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, file); } catch (Exception error) { throw new RuntimeException(error); } finally { bitmap.recycle(); }
        });
    }
    private android.widget.VideoView viewerVideo() throws Exception { PinViewer viewer = (PinViewer)field(main, "detailDialog"); return viewer == null ? null : (android.widget.VideoView)field(viewer, "video"); }
    private void fittedVideo(PinViewer viewer) throws Exception { onMain(() -> { try { View stage = (View)field(viewer, "stage"), video = (View)field(viewer, "video"); require(stage.getHeight() > main.getResources().getDisplayMetrics().heightPixels / 2, "Video is still confined to a small dialog."); require(video.getWidth() <= stage.getWidth() && video.getHeight() <= stage.getHeight() && Math.abs((double)video.getWidth() / video.getHeight() - 90.0 / 160) < .02, "Video was cropped, stretched or did not fit the viewer."); } catch (Exception error) { throw new RuntimeException(error); } }); }
    private void swipeViewer(PinViewer viewer, int x, int y) throws Exception { onMain(() -> { try { View stage = (View)field(viewer, "stage"); int[] location = new int[2]; stage.getLocationInWindow(location); float startX = location[0] + stage.getWidth() / 2f, startY = location[1] + stage.getHeight() / 2f; long now = SystemClock.uptimeMillis(); for (int action : new int[] {android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP}) { android.view.MotionEvent event = android.view.MotionEvent.obtain(now, now + (action == android.view.MotionEvent.ACTION_DOWN ? 0 : 150), action, startX + (action == android.view.MotionEvent.ACTION_DOWN ? 0 : x), startY + (action == android.view.MotionEvent.ACTION_DOWN ? 0 : y), 0); viewer.dispatchTouchEvent(event); event.recycle(); } } catch (Exception error) { throw new RuntimeException(error); } }); }
    private void connectionFeedback(String url, String capture) throws Exception {
        await(() -> !busy(), 5000, "Delivery was still running before the connection check.");
        PhoneStore store = PhoneStore.get(main); JSONObject config = store.snapshot().getJSONObject("config");
        store.endpoint("http://127.0.0.1:65534");
        String queued = store.enqueue(url + "?pending-dialog", "11111111-1111-1111-1111-111111111111", "Pending dialog check");
        JSONObject entry = store.snapshot().getJSONArray("entries").getJSONObject(0);
        String warning = "Links are waiting for your paired desktop. It reconnects automatically on this network. Send or dismiss those links in Activity before pairing a different desktop.";
        try {
            click(main, "Connections"); click(main, "Pair desktop"); click(main, "Scan QR");
            await(() -> has(warning), 5000, "Pairing warning was hidden behind Connections.");
            await(() -> { TextView feedback = (TextView)field(main, "connectionFeedback"); android.graphics.Rect visible = new android.graphics.Rect(); return feedback.getGlobalVisibleRect(visible) && visible.height() >= feedback.getHeight(); }, 3000, "Connection warning did not scroll into view.");
            screenshot(main, ((android.app.Dialog)field(main, "connectionsDialog")).getWindow(), capture);
            onMain(() -> {
                TextView feedback = (TextView)findWindow(warning); android.graphics.Rect visible = new android.graphics.Rect();
                require(feedback.getGlobalVisibleRect(visible) && visible.height() >= feedback.getHeight(), "Connection warning was clipped or scrolled out of view.");
                require(feedback.getBackground() == null && feedback.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_POLITE, "Connection feedback lost its inline/accessibility treatment.");
                View background = main.getWindow().getDecorView().findViewById(com.google.android.material.R.id.snackbar_text);
                require(background == null || !background.isShown(), "A snackbar remained behind the open Connections window.");
            });
            click(main, "Close"); click(main, "Connections"); click(main, "Pair desktop"); click(main, "Scan QR");
            await(() -> has(warning), 5000, "Reopening Connections hid the repeated pending-link warning.");
            input("http://192.168.1.10:47778", "http://127.0.0.1:65534"); click(main, "Update desktop address");
            await(() -> has("Desktop address updated; queued destinations were kept."), 3000, "Address confirmation was hidden behind Connections.");
            JSONObject kept = store.snapshot().getJSONArray("entries").getJSONObject(0);
            for (String key : new String[] {"id", "url", "collectionId", "collectionName"}) require(entry.getString(key).equals(kept.getString(key)), "Connection recovery changed a pending link or destination.");
            require(config.getString("token").equals(store.snapshot().getJSONObject("config").getString("token")), "Connection recovery replaced the pairing key.");
            click(main, "Close"); await(() -> main.hasWindowFocus(), 5000, "Closing Connections did not restore the board.");
            click(main, "Add link"); input("https://…", "https://example.com/keep-this-draft");
            onMain(() -> main.onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "invalid shared link")));
            require(has("Choose collection"), "Incoming invalid share closed the existing form.");
            onMain(() -> { View background = main.getWindow().getDecorView().findViewById(com.google.android.material.R.id.snackbar_text); require(background == null || !background.isShown(), "Incoming share feedback appeared behind the form."); });
            click(main, "Cancel");
            await(() -> has(((TextView)field(main, "status")).getText().toString()), 3000, "Deferred share error was lost when the form closed.");
        } finally {
            onMain(() -> { try { android.app.Dialog dialog = (android.app.Dialog)field(main, "connectionsDialog"); if (dialog != null) dialog.dismiss(); } catch (Exception error) { throw new RuntimeException(error); } });
            await(() -> !busy(), 15000, "Connection check delivery did not settle."); store.remove(queued, false); store.endpoint(config.getString("endpoint"));
        }
    }
    private android.net.nsd.NsdManager.RegistrationListener advertise(String serverId, String endpoint, String name) throws Exception {
        android.net.nsd.NsdManager nsd = (android.net.nsd.NsdManager)main.getSystemService(Context.NSD_SERVICE);
        android.net.nsd.NsdServiceInfo info = new android.net.nsd.NsdServiceInfo(); info.setServiceName("Papan " + serverId); info.setServiceType(DesktopDiscovery.TYPE); info.setPort(java.net.URI.create(endpoint).getPort());
        info.setAttribute("id", serverId); info.setAttribute("endpoint", endpoint); info.setAttribute("name", name); info.setAttribute("version", "1");
        java.util.concurrent.CountDownLatch registered = new java.util.concurrent.CountDownLatch(1); boolean[] success = new boolean[1];
        android.net.nsd.NsdManager.RegistrationListener listener = new android.net.nsd.NsdManager.RegistrationListener() {
            public void onServiceRegistered(android.net.nsd.NsdServiceInfo service) { success[0] = true; registered.countDown(); }
            public void onRegistrationFailed(android.net.nsd.NsdServiceInfo service, int error) { registered.countDown(); }
            public void onServiceUnregistered(android.net.nsd.NsdServiceInfo service) {}
            public void onUnregistrationFailed(android.net.nsd.NsdServiceInfo service, int error) {}
        };
        nsd.registerService(info, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, listener);
        require(registered.await(10000, java.util.concurrent.TimeUnit.MILLISECONDS) && success[0], "Native DNS-SD fixture did not register."); return listener;
    }
    private void nearbyPairing() throws Exception {
        JSONObject original = PhoneStore.get(main).snapshot().getJSONObject("config");
        android.net.nsd.NsdManager.RegistrationListener announcement = advertise(arguments.getString("pairServerId"), arguments.getString("pairEndpoint"), "Nearby test desktop");
        try {
            click(main, "Connections"); click(main, "Pair desktop");
            await(() -> has("Pair · Nearby test desktop"), 15000, "Nearby desktop was not discovered/resolved by Android DNS-SD.");
            click(main, "Pair · Nearby test desktop");
            await(() -> has("Approve this matching code in Papan on Nearby test desktop."), 10000, "Phone did not request desktop approval and display its code.");
            screenshot(main, ((android.app.Dialog)field(main, "nearbyDialog")).getWindow(), "nearby-pairing.png");
            require(original.getString("token").equals(PhoneStore.get(main).snapshot().getJSONObject("config").getString("token")), "Unapproved desktop replaced the existing pairing.");
            control("approve-pairing");
            await(() -> main.hasWindowFocus() && arguments.getString("pairServerId").equals(PhoneStore.get(main).snapshot().getJSONObject("config").getString("serverId")), 10000, "Approved desktop did not pair from the phone without a QR code.");
            await(() -> has("Nearby inbox"), 10000, "Phone-initiated pairing did not browse the selected desktop.");
            click(main, "Phone");
        } finally {
            ((android.net.nsd.NsdManager)main.getSystemService(Context.NSD_SERVICE)).unregisterService(announcement);
            PhoneStore.get(main).pair(original);
        }
    }
    private void collectionControls(String url) throws Exception {
        click(main, "Rename collection"); input("Collection name", ""); click(main, "Save");
        require(fieldError("Collection name"), "Empty collection name was accepted.");
        input("Collection name", "Pocket draft"); click(main, "Save");
        await(() -> has("Pocket draft"), 5000, "Collection rename did not update the tab.");
        click(main, "Rename collection"); input("Collection name", "Pocket"); click(main, "Save");
        click(main, "Add link"); input("https://…", "not a link"); click(main, "Choose collection");
        require(fieldError("https://…"), "Invalid link was accepted.");
        input("https://…", url); click(main, "Choose collection"); click(main, "Cancel");
        require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2 && PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0, "Cancelled sheet changed storage.");
        onMain(() -> main.onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)));
        click(main, "Phone · Study"); require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, "Share intent did not retain per-collection deduplication.");
        click(main, "Pocket");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: " + url); require(card != null, "Phone reference missing."); card.performClick(); });
        android.content.IntentFilter links = new android.content.IntentFilter(Intent.ACTION_VIEW); links.addDataScheme("http"); links.addDataScheme("https");
        ActivityMonitor source = addMonitor(links, new ActivityResult(Activity.RESULT_CANCELED, null), true);
        try { click(main, "Open original"); require(source.getHits() == 1, "Source did not route to a browser intent."); } finally { removeMonitor(source); }
        click(main, "Save to another collection"); click(main, "Phone · Study");
        require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, "Saving to an existing destination duplicated the reference.");
        click(main, "New collection"); input("Collection name", "Temporary check"); click(main, "Create");
        click(main, "Delete collection"); click(main, "Keep");
        require(MobileLibrary.get(main).snapshot().getJSONArray("collections").length() == 3, "Delete cancellation removed the collection.");
        String failedUrl = url.replace("/wide.png", "/missing");
        clip(failedUrl); click(main, "Paste link"); click(main, "Phone · Temporary check");
        await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 3 && "failed".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2).optString("capture")), 10000, "Failed capture did not retain its link.");
        await(() -> has("Open pin: " + failedUrl) && text(main).contains("Preview unavailable"), 5000, "Failed preview did not expose its status and link."); alignedBoard(); screenshot(main, "failed-board.png");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: " + failedUrl); require(card != null, "Failed reference missing."); card.performClick(); });
        click(main, "Retry download");
        await(() -> "failed".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2).optString("capture")), 10000, "Capture retry did not settle.");
        click(main, "Delete collection"); click(main, "Delete");
        await(() -> MobileLibrary.get(main).snapshot().getJSONArray("collections").length() == 2 && MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, 5000, "Collection delete affected the wrong references.");
        click(main, "Connections"); input("Complete pairing link", "not a pairing link"); click(main, "Pair from link");
        await(() -> !((TextView)field(main, "status")).getText().toString().equals("Pairing…"), 5000, "Invalid manual pairing did not return an error.");
        require(has(((TextView)field(main, "status")).getText().toString()), "Manual pairing error was hidden behind Connections.");
        JSONObject config = PhoneStore.get(main).snapshot().getJSONObject("config");
        input("http://192.168.1.10:47778", config.getString("endpoint")); click(main, "Update desktop address");
        require(config.toString().equals(PhoneStore.get(main).snapshot().getJSONObject("config").toString()), "Address recovery changed the pairing.");
        click(main, "Close");
        await(() -> main.hasWindowFocus(), 5000, "Main window did not regain keyboard focus.");
        key(android.view.KeyEvent.KEYCODE_TAB);
        onMain(() -> { try { View fab = (View)field(main, "addLink"); fab.requestFocus(); require(fab.isFocused(), "Material Add link could not receive keyboard focus."); } catch (Exception error) { throw new RuntimeException(error); } });
        key(android.view.KeyEvent.KEYCODE_ENTER);
        await(() -> has("Choose collection"), 5000, "Keyboard Enter did not activate Add link."); key(android.view.KeyEvent.KEYCODE_BACK);
        await(() -> main.hasWindowFocus() && !has("Choose collection"), 5000, "Android Back did not dismiss the form.");
    }
    private void key(int code) throws Exception {
        require(displayId != Display.DEFAULT_DISPLAY && display.getDisplay().getDisplayId() == displayId, "Refusing keyboard input outside the isolated display.");
        try (android.os.ParcelFileDescriptor.AutoCloseInputStream result = new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("input -d " + displayId + " keyevent " + code))) { byte[] bytes = new byte[1024]; require(result.read(bytes) == -1, "Isolated keyboard command failed."); }
        waitForIdleSync();
    }
    private void tapLocationButton() throws Exception {
        require(displayId != Display.DEFAULT_DISPLAY && main.getDisplay().getDisplayId() == displayId, "Refusing a tap outside the isolated display."); int[] point = new int[2];
        onMain(() -> { try { View button = (View)field(main, "locationButton"); button.getLocationOnScreen(point); point[0] += button.getWidth() / 2; point[1] += button.getHeight() / 2; } catch (Exception error) { throw new RuntimeException(error); } });
        try (android.os.ParcelFileDescriptor.AutoCloseInputStream output = new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("input -d " + displayId + " tap " + point[0] + " " + point[1]))) { require(output.read() == -1, "Isolated button tap failed."); } waitForIdleSync();
    }
    private boolean fieldError(String hint) {
        boolean[] found = new boolean[1];
        onMain(() -> { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { EditText value = inputView(window, hint); if (value != null && ((com.google.android.material.textfield.TextInputLayout)value.getTag()).getError() != null) found[0] = true; } });
        return found[0];
    }
    private void createDisplay(int width, int height) {
        output = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        output.setOnImageAvailableListener(reader -> { try (Image frame = reader.acquireLatestImage()) {} }, new Handler(Looper.getMainLooper()));
        // Own unlocked display group/focus; never steal focus from a physical display.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            display = ((DisplayManager)getTargetContext().getSystemService(Context.DISPLAY_SERVICE)).createVirtualDisplay(
                "Papan isolated UI test", width, height, 320, output.getSurface(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | (1 << 8) | (1 << 10) | (1 << 11) | (1 << 12) | (1 << 13) | (1 << 14) | (1 << 16));
        } finally { getUiAutomation().dropShellPermissionIdentity(); }
        require(display != null && "Papan isolated UI test".equals(display.getDisplay().getName()), "An isolated virtual display is required.");
        displayId = display.getDisplay().getDisplayId(); require(displayId != Display.DEFAULT_DISPLAY, "Refusing the physical display.");
    }
    private void relaunch(int width, int height, float scale) throws Exception {
        onMain(() -> main.finishAndRemoveTask()); await(() -> main.isDestroyed(), 5000, "Previous test activity did not close."); waitForIdleSync();
        display.release(); output.close(); createDisplay(width, height); testFontScale = scale;
        main = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
        require(main.getDisplay().getDisplayId() == displayId, "Resized activity opened outside the virtual display."); await(() -> main.hasWindowFocus() && !busy(), 10000, "Resized activity did not initialize.");
    }
    private GridLayoutManager grid() throws Exception { return (GridLayoutManager)((RecyclerView)field(main, "pinGrid")).getLayoutManager(); }
    private void styleBoard() throws Exception {
        relaunch(720, 1600, 1);
        MobileLibrary store = MobileLibrary.get(main); String collection = store.create("Color & light");
        org.json.JSONArray samples = new org.json.JSONArray(new String(java.nio.file.Files.readAllBytes(new File(main.getFilesDir(), "style-demo.json").toPath()), java.nio.charset.StandardCharsets.UTF_8));
        for (int i = samples.length() * 2 - 1; i >= 0; i--) { JSONObject sample = samples.getJSONObject(i % samples.length()); String id = store.add(collection, sample.getString("sourceUrl") + (i >= samples.length() ? "?grid-check=2" : "")); store.captured(id, new JSONObject().put("title", sample.getString("title")).put("mediaFile", sample.getString("mediaFile")).put("kind", "image").put("capture", "saved")); }
        onMain(() -> { try { Field selected = MainActivity.class.getDeclaredField("selectedLocal"); selected.setAccessible(true); selected.set(main, collection); Method refresh = MainActivity.class.getDeclaredMethod("refreshLocal"); refresh.setAccessible(true); refresh.invoke(main); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> hasImage(main.getWindow().getDecorView()), 10000, "Artwork preview did not decode.");
        onMain(() -> require(imageFillsFrame(main.getWindow().getDecorView()), "Default phone previews do not zoom and crop to fill their tiles."));
        onMain(() -> { try { RecyclerView board = (RecyclerView)field(main, "pinGrid"); board.getChildAt(0).performClick(); } catch (Exception error) { throw new RuntimeException(error); } });
        PinViewer imageViewer = (PinViewer)field(main, "detailDialog"); await(() -> hasImage(imageViewer.getWindow().getDecorView()), 10000, "Full-screen reference image did not decode."); screenshot(main, imageViewer.getWindow(), "image-viewer.png");
        onMain(() -> { ZoomImageView image = zoomImage(imageViewer.getWindow().getDecorView()); require(image != null, "Full-screen image does not support zoom."); long now = SystemClock.uptimeMillis(); for (int tap = 0; tap < 2; tap++) for (int action : new int[] {android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) { android.view.MotionEvent event = android.view.MotionEvent.obtain(now + tap * 100, now + tap * 100 + (action == android.view.MotionEvent.ACTION_UP ? 20 : 0), action, image.getWidth() / 2f, image.getHeight() / 2f, 0); image.onTouchEvent(event); event.recycle(); } require(image.zoomed(), "Double tap did not zoom the reference image."); image.requestFocus(); image.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)); require(!image.zoomed(), "Keyboard activation did not reset image zoom."); });
        click(main, "Close reference");
        if (((View)field(main, "searchLayout")).isShown()) click(main, "Search");
        resizeBoard(100, 3, "style-small.png");
        relaunch(720, 1600, 1); require(collection.equals(field(main, "selectedLocal")) && grid().getSpanCount() == 3, "Chosen collection/preview size did not persist.");
        click(main, "Pocket"); await(() -> grid().getSpanCount() == 3, 5000, "Preview sizing changed an unrelated collection."); click(main, "Color & light"); await(() -> grid().getSpanCount() == 3, 5000, "Collection-specific sizing was lost.");
        resizeBoard(300, 1, "style-large.png"); resizeBoard(160, 2, "style-light.png");
        onMain(() -> { try { RecyclerView board = (RecyclerView)field(main, "pinGrid"); board.scrollBy(0, board.getChildAt(0).getHeight() * 2); require(grid().findFirstVisibleItemPosition() >= 2, "Native board scroll did not move its visible cards."); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> grid().findFirstVisibleItemPosition() >= 2, 5000, "Artwork board did not scroll."); int anchor = grid().findFirstVisibleItemPosition();
        resizeBoard(300, 1, null); require(grid().findFirstVisibleItemPosition() == anchor, "Resizing jumped away from the visible reference."); resizeBoard(160, 2, null);
        onMain(() -> { try { ((RecyclerView)field(main, "pinGrid")).scrollToPosition(0); } catch (Exception error) { throw new RuntimeException(error); } }); await(() -> grid().findFirstVisibleItemPosition() == 0, 5000, "Artwork board did not return to its first row.");
        click(main, "Board appearance"); await(() -> has("Preview size"), 5000, "Appearance panel did not open."); screenshot(main, ((android.app.Dialog)field(main, "appearanceDialog")).getWindow(), "style-appearance.png"); click(main, "Fill image frames"); click(main, "Close");
        await(() -> hasImage(main.getWindow().getDecorView()), 5000, "Whole-image frames did not load.");
        onMain(() -> require(imageScale(main.getWindow().getDecorView(), android.widget.ImageView.ScaleType.FIT_CENTER), "Whole-image setting did not update actual previews."));
        MainActivity wholeImage = main; relaunch(720, 1600, 1);
        await(() -> main != wholeImage && hasImage(main.getWindow().getDecorView()), 5000, "Whole-image preference did not reopen.");
        onMain(() -> require(imageScale(main.getWindow().getDecorView(), android.widget.ImageView.ScaleType.FIT_CENTER), "Explicit whole-image preference did not persist."));
        click(main, "Board appearance"); click(main, "Fill image frames"); MainActivity beforeDark = main; click(main, "Dark");
        await(() -> main != beforeDark && main.hasWindowFocus() && (main.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES && hasImage(main.getWindow().getDecorView()), 10000, "Dark appearance did not recreate correctly.");
        require(main.getDisplay().getDisplayId() == displayId, "Theme change left the isolated display."); alignedBoard(); screenshot(main, "style-dark.png");
        ActivityMonitor darkScanner = addMonitor(ScanActivity.class.getName(), null, false);
        try { click(main, "Connections"); click(main, "Pair desktop"); click(main, "Scan QR"); await(() -> darkScanner.getLastActivity() != null && darkScanner.getLastActivity().hasWindowFocus(), 5000, "Dark pairing screen did not open."); Activity scanner = darkScanner.getLastActivity(); require(scanner.getDisplay().getDisplayId() == displayId && (scanner.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES, "Pairing screen did not inherit the selected appearance."); click(scanner, "Cancel"); } finally { removeMonitor(darkScanner); }
        click(main, "Close");
        click(main, "Board appearance"); MainActivity beforeLight = main; click(main, "Light"); await(() -> main != beforeLight && main.hasWindowFocus() && (main.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_NO && hasImage(main.getWindow().getDecorView()), 10000, "Light appearance did not recreate correctly.");
        alignedBoard(); screenshot(main, "style-light.png");
        onMain(() -> { try { com.google.android.material.appbar.MaterialToolbar toolbar = (com.google.android.material.appbar.MaterialToolbar)field(main, "toolbar"); for (int action : new int[] {107, 108}) require(toolbar.getMenu().findItem(action).getIconTintList().getDefaultColor() == main.getColor(R.color.papan_on_surface_variant), "Toolbar icons kept a low-contrast tint after switching themes."); } catch (Exception error) { throw new RuntimeException(error); } });
        click(main, "Board appearance"); click(main, "Use phone setting"); await(() -> main.hasWindowFocus() && ((android.content.SharedPreferences)field(main, "appearance")).getInt("theme", 0) == -1, 10000, "System appearance did not persist.");
        relaunch(720, 1600, 2); require(grid().getSpanCount() == 1, "Large captions did not reflow to one column."); await(() -> hasImage(main.getWindow().getDecorView()), 10000, "Large-text artwork did not decode."); alignedBoard(); screenshot(main, "style-large-text.png");
    }
    private ZoomImageView zoomImage(View view) { if (view instanceof ZoomImageView) return (ZoomImageView)view; if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) { ZoomImageView image = zoomImage(((ViewGroup)view).getChildAt(i)); if (image != null) return image; } return null; }
    private boolean imageScale(View root, ImageView.ScaleType expected) { if (root instanceof ImageView && root.getTag() instanceof TextView && ((ImageView)root).getDrawable() instanceof android.graphics.drawable.BitmapDrawable) return ((ImageView)root).getScaleType() == expected; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) if (imageScale(((ViewGroup)root).getChildAt(i), expected)) return true; return false; }
    private boolean imageFillsFrame(View root) {
        if (root instanceof ImageView && root.getTag() instanceof TextView && ((ImageView)root).getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            ImageView image = (ImageView)root; android.graphics.drawable.Drawable drawable = image.getDrawable();
            android.graphics.RectF bounds = new android.graphics.RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight()); image.getImageMatrix().mapRect(bounds);
            require(image.getScaleType() == ImageView.ScaleType.CENTER_CROP && bounds.left <= 1 && bounds.top <= 1 && bounds.right >= image.getWidth() - 1 && bounds.bottom >= image.getHeight() - 1, "Decoded preview leaves unfilled tile edges.");
            double sourceRatio = (double)drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight(); require(Math.abs(bounds.width() / bounds.height() - sourceRatio) < .01, "Filling the preview stretches the image.");
            if (Math.abs(sourceRatio - (double)image.getWidth() / image.getHeight()) > .05) require(bounds.width() > image.getWidth() + 1 || bounds.height() > image.getHeight() + 1, "Mismatched image proportions did not crop.");
            return true;
        }
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) if (imageFillsFrame(((ViewGroup)root).getChildAt(i))) return true;
        return false;
    }
    private void resizeBoard(int target, int columns, String capture) throws Exception {
        click(main, "Board appearance");
        await(() -> has("Preview size"), 5000, "Board appearance did not receive focus.");
        onMain(() -> { com.google.android.material.slider.Slider size = null; for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { View found = description(window, "Preview size"); if (found instanceof com.google.android.material.slider.Slider) size = (com.google.android.material.slider.Slider)found; } require(size != null, "Preview-size control is missing."); size.requestFocus(); int steps = Math.round((target - size.getValue()) / 20); int key = steps > 0 ? android.view.KeyEvent.KEYCODE_DPAD_RIGHT : android.view.KeyEvent.KEYCODE_DPAD_LEFT; for (int i = 0; i < Math.abs(steps); i++) { size.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key)); size.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key)); } require(size.getValue() == target, "Native size slider did not accept keyboard input."); });
        click(main, "Close"); await(() -> grid().getSpanCount() == columns && hasImage(main.getWindow().getDecorView()), 10000, "Preview size did not change the board."); alignedBoard(); if (capture != null) screenshot(main, capture);
    }
    private void alignedBoard() throws Exception { waitForIdleSync(); onMain(() -> { try {
        RecyclerView view = (RecyclerView)field(main, "pinGrid"); int columns = grid().getSpanCount(), height = -1, width = -1, checked = 0;
        java.util.HashMap<Integer,Integer> left = new java.util.HashMap<>(), top = new java.util.HashMap<>();
        for (int i = 0; i < view.getChildCount(); i++) { View card = view.getChildAt(i); int position = view.getChildAdapterPosition(card); if (position < 0 || view.getAdapter().getItemViewType(position) != 0) continue;
            if (height < 0) { height = card.getHeight(); width = card.getWidth(); }
            require(Math.abs(card.getHeight() - height) <= 1 && Math.abs(card.getWidth() - width) <= 1, "Mixed media/title cards have unequal frames.");
            Integer x = left.putIfAbsent(position % columns, card.getLeft()), y = top.putIfAbsent(position / columns, card.getTop());
            require((x == null || x == card.getLeft()) && (y == null || y == card.getTop()), "Card rows or columns are misaligned."); checked++;
            RecyclerView.ViewHolder beside = position % columns == 0 ? null : view.findViewHolderForAdapterPosition(position - 1), above = view.findViewHolderForAdapterPosition(position - columns);
            if (beside != null) require(card.getLeft() == beside.itemView.getRight(), "Empty space or overlap remains between pin columns.");
            if (above != null) require(card.getTop() == above.itemView.getBottom(), "Empty space or overlap remains between pin rows.");
            if (position % columns == 0) require(card.getLeft() == 0, "Preview row does not reach the left edge.");
            if (position % columns == columns - 1) require(card.getRight() == view.getWidth(), "Preview row does not reach the right edge.");
            previewCaption(card);
        }
        require(checked >= Math.min(columns, view.getAdapter().getItemCount()), "No full row was checked.");
    } catch (Exception error) { throw new RuntimeException(error); } }); }
    private void previewCaption(View card) {
        String label = card.getContentDescription().toString().substring("Open pin: ".length());
        TextView title = captionText(card, label); require(title != null, "Full pin title is missing from the preview.");
        ViewGroup caption = (ViewGroup)title.getParent(); require(caption.getChildCount() >= 2 && caption.getChildAt(1) instanceof TextView, "Pin source is missing from the caption.");
        require(!((TextView)caption.getChildAt(1)).getText().toString().startsWith("www."), "Pin source kept desktop's omitted www prefix.");
        View preview = (View)((View)caption.getParent()).getParent();
        require(Math.abs(card.getHeight() - preview.getHeight()) <= 2 && Math.abs(card.getWidth() - preview.getWidth()) <= 2, "Caption added height outside the preview frame.");
        require(caption.getBackground() == null, "Caption brought back the solid background block.");
        View overlay = (View)caption.getParent(); require(overlay.getBackground() instanceof android.graphics.drawable.GradientDrawable, "Caption lost the desktop fade.");
        ViewGroup previewRoot = (ViewGroup)preview;
        for (int i = 0; i < previewRoot.getChildCount(); i++) if (previewRoot.getChildAt(i) instanceof TextView) { TextView stateLabel = (TextView)previewRoot.getChildAt(i); int background = ((android.graphics.drawable.ColorDrawable)stateLabel.getBackground()).getColor(); require(i > previewRoot.indexOfChild(overlay) && android.graphics.Color.alpha(background) == 255 && androidx.core.graphics.ColorUtils.calculateContrast(stateLabel.getCurrentTextColor(), background) >= 4.5, "Preview status/type label was obscured by the fade."); }
        Bitmap fade = Bitmap.createBitmap(overlay.getWidth(), overlay.getHeight(), Bitmap.Config.ARGB_8888); android.graphics.Canvas canvas = new android.graphics.Canvas(fade); canvas.drawColor(android.graphics.Color.WHITE); overlay.getBackground().draw(canvas);
        require(fade.getPixel(0, 0) != fade.getPixel(0, fade.getHeight() / 2) && fade.getPixel(0, fade.getHeight() / 2) != fade.getPixel(0, fade.getHeight() - 1), "Caption fade became a flat background.");
        int[] frame = new int[2]; preview.getLocationOnScreen(frame);
        int[] fadePoint = new int[2]; overlay.getLocationOnScreen(fadePoint);
        try {
        for (int i = 0; i < caption.getChildCount(); i++) { TextView text = (TextView)caption.getChildAt(i); int[] point = new int[2]; text.getLocationOnScreen(point);
            require(text.isShown() && text.getMaxLines() == 1 && text.getLayout() != null && text.getLayout().getLineCount() == 1 && text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() >= text.getLayout().getHeight(), "Caption wrapped or clipped at the current font size.");
            require(point[0] >= frame[0] && point[1] >= frame[1] && point[0] + text.getWidth() <= frame[0] + preview.getWidth() && point[1] + text.getHeight() <= frame[1] + preview.getHeight(), "Caption escaped the preview image.");
            for (int y = point[1] - fadePoint[1]; y < point[1] - fadePoint[1] + text.getHeight(); y++) { double contrast = androidx.core.graphics.ColorUtils.calculateContrast(text.getCurrentTextColor(), fade.getPixel(0, y)); require(contrast >= 4.5, "Caption loses contrast over a white preview."); if (i == 0) captionTitleContrast = Math.min(captionTitleContrast, contrast); else captionSourceContrast = Math.min(captionSourceContrast, contrast); }
        }
        } finally { fade.recycle(); }
    }
    private TextView captionText(View root, String value) { if (root instanceof TextView && value.contentEquals(((TextView)root).getText())) return (TextView)root; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { TextView found = captionText(((ViewGroup)root).getChildAt(i), value); if (found != null) return found; } return null; }
    private void responsive() throws Exception {
        relaunch(600, 1400, 1); require(grid().getSpanCount() == 1, "Narrow phone did not use one column."); controlsFit(); alignedBoard(); screenshot(main, "narrow.png");
        relaunch(1280, 1600, 1); require(grid().getSpanCount() == 5, "Tablet did not use the space released by navigation."); controlsFit(); await(() -> hasImage(main.getWindow().getDecorView()), 5000, "Tablet preview did not decode."); alignedBoard(); screenshot(main, "tablet.png");
        relaunch(720, 1600, 2); require(main.getResources().getConfiguration().fontScale == 2 && grid().getSpanCount() == 1, "Large font layout did not reflow."); controlsFit(); alignedBoard(); screenshot(main, "large-text.png");
    }
    private void controlsFit() throws Exception { onMain(() -> { try {
        View root = (View)field(main, "root"), fab = (View)field(main, "addLink");
        View location = (View)field(main, "locationButton"), collections = (View)field(main, "collectionButton");
        for (View control : new View[] {fab, location, collections}) {
            int[] point = new int[2]; control.getLocationOnScreen(point); require(control.getWidth() >= 96 && control.getHeight() >= 96 && point[0] >= 0 && point[0] + control.getWidth() <= root.getWidth() && point[1] + control.getHeight() <= root.getHeight(), "Collection/location picker or Add link clipped or below 48dp touch target.");
        }
        require(Math.abs(location.getTop() + location.getHeight() / 2 - fab.getTop() - fab.getHeight() / 2) <= 1 && location.getRight() < fab.getLeft(), "Location picker and Add link do not share a clear bottom row.");
        require(((View)field(main, "pinGrid")).getPaddingBottom() >= ((View)location.getParent()).getHeight(), "Floating controls cover the final pin at this font scale.");
        View toolbar = (View)field(main, "toolbar"), pins = (View)field(main, "pinGrid"); int[] bar = new int[2], board = new int[2]; toolbar.getLocationOnScreen(bar); pins.getLocationOnScreen(board);
        require(pins.getHeight() > 0 && collections.getTop() >= 0 && collections.getBottom() <= toolbar.getHeight(), "Collection picker did not stay in the app bar.");
        if (!((View)field(main, "searchLayout")).isShown()) require(board[1] - bar[1] - toolbar.getHeight() <= 8, "An extra collection/navigation row still takes preview space.");
    } catch (Exception error) { throw new RuntimeException(error); } });
        key(android.view.KeyEvent.KEYCODE_TAB);
        onMain(() -> { try { View location = (View)field(main, "locationButton"); location.requestFocus(); require(location.isFocused(), "Location chooser could not receive keyboard focus."); } catch (Exception error) { throw new RuntimeException(error); } }); key(android.view.KeyEvent.KEYCODE_ENTER);
        await(() -> !has("Phone") && has("Desktop") && has("Activity"), 5000, "Location menu repeated the current window or lost an alternative."); popupFits("location-menu.png");
        key(android.view.KeyEvent.KEYCODE_DPAD_DOWN); android.widget.ListView alternatives = ((androidx.appcompat.widget.ListPopupWindow)field(main, "locationMenu")).getListView(); require(alternatives.getSelectedItemPosition() >= 0 && alternatives.getCheckedItemCount() == 0, "Arrow key did not focus an unchosen alternative."); if (alternatives.getSelectedItemPosition() == 0) key(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
        onMain(() -> { try { android.widget.ListView choices = ((androidx.appcompat.widget.ListPopupWindow)field(main, "locationMenu")).getListView(); require(choices.getSelectedItemPosition() == 1 && choices.getCheckedItemCount() == 0 && "Phone".contentEquals(((TextView)field(main, "locationButton")).getText()), "Arrow navigation changed the active window."); View focused = choices.getSelectedView(); require(focused != null && focused.getBackground().getCurrent() instanceof android.graphics.drawable.ColorDrawable && ((android.graphics.drawable.ColorDrawable)focused.getBackground().getCurrent()).getColor() == main.getColor(R.color.papan_secondary_container), "Keyboard menu focus has no visible highlight."); } catch (Exception error) { throw new RuntimeException(error); } }); popupFits("location-focus-menu.png");
        key(android.view.KeyEvent.KEYCODE_BACK); await(main::hasWindowFocus, 5000, "Location menu did not dismiss.");
        locationSizing();
        key(android.view.KeyEvent.KEYCODE_TAB);
        onMain(() -> { try { View collections = (View)field(main, "collectionButton"); collections.requestFocus(); require(collections.isFocused(), "Collection dropdown could not receive keyboard focus."); } catch (Exception error) { throw new RuntimeException(error); } }); key(android.view.KeyEvent.KEYCODE_ENTER);
        await(() -> !main.hasWindowFocus(), 5000, "Keyboard did not open the collection dropdown."); popupFits("collection-menu.png"); key(android.view.KeyEvent.KEYCODE_BACK); await(main::hasWindowFocus, 5000, "Collection dropdown did not dismiss.");
    }
    private void popupFits(String capture) throws Exception {
        await(() -> { boolean[] ready = new boolean[1]; onMain(() -> { for (View window : WindowInspector.getGlobalWindowViews()) if (window != main.getWindow().getDecorView() && window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) ready[0] = menuSettled(window); }); return ready[0]; }, 5000, "Picker entrance animation did not settle.");
        onMain(() -> {
        View popup = null; for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId && window != main.getWindow().getDecorView()) popup = window;
        require(popup != null, "Picker did not use a focused native popup on the isolated display."); int[] point = new int[2]; popup.getLocationOnScreen(point); android.util.DisplayMetrics metrics = main.getResources().getDisplayMetrics();
        require(point[0] >= 0 && point[1] >= 0 && point[0] + popup.getWidth() <= metrics.widthPixels && point[1] + popup.getHeight() <= metrics.heightPixels, "Native picker menu extends outside the window.");
        android.widget.ListView choices = pickerList(popup); require(choices != null, "Picker has no native list.");
        if (choices.getChoiceMode() == android.widget.ListView.CHOICE_MODE_SINGLE) {
            require(choices.getCheckedItemCount() == 1, "Collection picker did not expose its selected choice."); View selected = choices.getChildAt(choices.getCheckedItemPosition() - choices.getFirstVisiblePosition()); require(selected != null && selected.getBackground().getCurrent() instanceof android.graphics.drawable.ColorDrawable && ((android.graphics.drawable.ColorDrawable)selected.getBackground().getCurrent()).getColor() == main.getColor(R.color.papan_primary_container), "Current collection row has no green highlight.");
        } else { require(choices.getCount() == 2 && choices.getCheckedItemCount() == 0, "Location menu is not limited to two inactive alternatives."); try { int[] button = new int[2]; ((View)field(main, "locationButton")).getLocationOnScreen(button); require(point[1] + popup.getHeight() <= button[1], "Location popup covers its button."); } catch (Exception error) { throw new RuntimeException(error); } }
        require(!containsCheckbox(popup), "Picker still displays a checkbox.");
        Bitmap bitmap = Bitmap.createBitmap(popup.getWidth(), popup.getHeight(), Bitmap.Config.ARGB_8888); popup.draw(new android.graphics.Canvas(bitmap));
        try (FileOutputStream file = new FileOutputStream(new File(main.getFilesDir(), capture))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, file); } catch (Exception error) { throw new RuntimeException(error); } finally { bitmap.recycle(); }
    }); }
    private void compactControls() throws Exception {
        require(displayId != Display.DEFAULT_DISPLAY && main.getDisplay().getDisplayId() == displayId, "Refusing interaction outside the isolated display.");
        int[] point = new int[2];
        onMain(() -> { try {
            float density = main.getResources().getDisplayMetrics().density; int touch = Math.round(48 * density), face = Math.round(40 * density);
            com.google.android.material.floatingactionbutton.FloatingActionButton add = (com.google.android.material.floatingactionbutton.FloatingActionButton)field(main, "addLink");
            com.google.android.material.button.MaterialButton location = (com.google.android.material.button.MaterialButton)field(main, "locationButton");
            android.graphics.Rect visible = new android.graphics.Rect(); require(add.getContentRect(visible) && visible.width() == face && visible.height() == face, "Add still covers more than a mini button face.");
            require(add.getWidth() >= touch && add.getHeight() >= touch && location.getWidth() >= touch && location.getHeight() >= touch, "Compact controls lost their Android tap targets.");
            if (testFontScale == 1) require(location.getHeight() - location.getInsetTop() - location.getInsetBottom() == face, "Location button face did not become compact.");
            View row = (View)location.getParent(), grid = (View)field(main, "pinGrid"), body = (View)field(main, "board");
            require(grid.getPaddingBottom() == row.getHeight() + Math.round(8 * density) && body.getPaddingBottom() == grid.getPaddingBottom(), "Bottom clearance does not follow compact controls.");
            add.getLocationOnScreen(point); point[0] += Math.max(1, visible.left / 2); point[1] += add.getHeight() / 2;
        } catch (Exception error) { throw new RuntimeException(error); } });
        try (android.os.ParcelFileDescriptor.AutoCloseInputStream result = new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("input -d " + displayId + " tap " + point[0] + " " + point[1]))) { require(result.read() == -1, "Virtual Add tap failed."); }
        await(() -> has("Add a link"), 5000, "Add did not accept a tap outside its visible face."); click(main, "Cancel"); await(main::hasWindowFocus, 5000, "Add dialog did not dismiss.");
    }
    private void locationSizing() throws Exception {
        compactControls();
        TextView button = (TextView)field(main, "locationButton"), collection = (TextView)field(main, "collectionButton"); int width = button.getWidth(), height = button.getHeight(); int menuWidth = -1, menuHeight = -1;
        require(button.getTextSize() == collection.getTextSize() && button.getTypeface().equals(collection.getTypeface()), "Picker buttons use inconsistent typography.");
        for (String label : new String[] {"Desktop", "Activity", "Phone"}) {
            click(main, label);
            if (label.equals("Desktop") && PhoneStore.get(main).snapshot().has("config")) {
                await(() -> collectionAvailable("Android chosen") && !busy(), 10000, "Unprotected collection fixture did not load."); click(main, "Android chosen");
                await(() -> !busy() && (main.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE) == 0, 10000, "Pointer checks require an unprotected fixture on the nonsecure virtual display.");
            }
            await(() -> button.getWidth() == width && button.getHeight() == height, 5000, "Location switching changed the button dimensions.");
            onMain(() -> { require(button.isEnabled() && button.isClickable() && label.contentEquals(button.getText()), "Current location is unavailable on its button."); require(button.getLayout() != null && button.getLayout().getLineCount() == 1 && button.getLayout().getEllipsisCount(0) == 0 && button.getHeight() - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom() >= button.getLayout().getHeight(), "Current location label wraps or clips at this font scale: " + label); button.performClick(); });
            await(() -> !main.hasWindowFocus(), 5000, "Location button did not open."); popupFits("location-" + label.toLowerCase(java.util.Locale.ROOT) + "-menu.png"); require(!has(label), "Active location is repeated in the menu.");
            androidx.appcompat.widget.ListPopupWindow menu = (androidx.appcompat.widget.ListPopupWindow)field(main, "locationMenu"); android.widget.ListView rows = menu.getListView();
            if (menuWidth < 0) { menuWidth = rows.getWidth(); menuHeight = rows.getHeight(); } else require(rows.getWidth() == menuWidth && rows.getHeight() == menuHeight, "Location menus change dimensions across windows.");
            onMain(() -> { for (int i = 0; i < rows.getChildCount(); i++) { TextView row = (TextView)rows.getChildAt(i); require(row.getTextSize() == button.getTextSize() && row.getTypeface().equals(button.getTypeface()), "Menu and button labels use inconsistent typography."); require(row.getLayout().getLineCount() == 1 && row.getLayout().getEllipsisCount(0) == 0, "Location menu label wraps or clips."); } });
            tapLocationButton(); await(main::hasWindowFocus, 5000, "Current button could not dismiss the menu."); require(label.contentEquals(button.getText()), "Current button changed the active window.");
        }
    }
    private void locationScrolling() throws Exception {
        relaunch(720, 420, 2);
        onMain(() -> { try { ((View)field(main, "locationButton")).performClick(); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> !main.hasWindowFocus(), 3000, "Short-display location menu did not open.");
        androidx.appcompat.widget.ListPopupWindow menu = (androidx.appcompat.widget.ListPopupWindow)field(main, "locationMenu"); android.widget.ListView rows = menu.getListView();
        onMain(() -> { require(rows.canScrollVertically(1), "Constrained location menu cannot scroll to its second destination."); rows.smoothScrollBy(rows.getHeight(), 100); });
        await(() -> rows.canScrollVertically(-1), 3000, "Location menu did not scroll.");
        click(main, "Activity"); await(main::hasWindowFocus, 3000, "Scrolled location was not selectable.");
        require("Activity".contentEquals(((TextView)field(main, "locationButton")).getText()), "Scrolled menu changed the wrong location.");
    }
    private boolean menuSettled(View view) { if (!view.isShown()) return true; if (view.getAlpha() < .999f || Math.abs(view.getTranslationX()) > .5f || Math.abs(view.getTranslationY()) > .5f || Math.abs(view.getScaleX() - 1) > .001f || Math.abs(view.getScaleY() - 1) > .001f || view.getAnimation() != null && !view.getAnimation().hasEnded()) return false; if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) if (!menuSettled(((ViewGroup)view).getChildAt(i))) return false; return true; }
    private android.widget.ListView pickerList(View view) { if (view instanceof android.widget.ListView) return (android.widget.ListView)view; if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) { android.widget.ListView found = pickerList(((ViewGroup)view).getChildAt(i)); if (found != null) return found; } return null; }
    private boolean containsCheckbox(View view) { if (view instanceof android.widget.CompoundButton) return true; if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) if (containsCheckbox(((ViewGroup)view).getChildAt(i))) return true; return false; }
    private void control(String action) throws Exception { Delivery.request(arguments.getString("controlEndpoint"), "/" + action, "", new JSONObject()); }
    private boolean protectedAuthorized(DesktopLibrary client) throws Exception { org.json.JSONArray values = client.collections(); for (int i = 0; i < values.length(); i++) { JSONObject value = values.getJSONObject(i); if ("Protected test".equals(value.optString("name"))) return !value.optBoolean("locked"); } return false; }
    private void remoteBoards(String url) throws Exception {
        click(main, "Desktop"); await(() -> collectionAvailable("Android chosen"), 10000, "Open desktop collections did not load.");
        clip(url + "?chosen"); click(main, "Paste link"); await(() -> has("Desktop · Android chosen"), 10000, "Desktop destination picker did not load.");
        control("disable"); click(main, "Desktop · Android chosen");
        await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 1 && !busy(), 15000, "Offline desktop choice was not retained.");
        JSONObject pending = PhoneStore.get(main).snapshot().getJSONArray("entries").getJSONObject(0); require(pending.has("collectionId") && "Android chosen".equals(pending.optString("collectionName")), "Selected collection was lost.");
        String requestId = pending.getString("id"), destination = pending.getString("collectionId");
        MainActivity closing = main; onMain(closing::finish); await(closing::isDestroyed, 5000, "Offline activity did not finish closing before restart."); waitForIdleSync(); Field singleton = PhoneStore.class.getDeclaredField("instance"); singleton.setAccessible(true); singleton.set(null, null);
        main = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
        require(main.getDisplay().getDisplayId() == displayId, "Restart opened on physical display."); await(() -> main.hasWindowFocus() && !busy(), 15000, "Restart did not settle.");
        require(((DesktopLibrary)field(main, "desktop")).sessionId().equals(DesktopConnection.sessionId()), "Restarted board and delivery use different app sessions.");
        JSONObject reopened = PhoneStore.get(main).snapshot().getJSONArray("entries").getJSONObject(0); require(requestId.equals(reopened.getString("id")) && destination.equals(reopened.getString("collectionId")), "Restart changed request identity/destination.");
        JSONObject paired = PhoneStore.get(main).snapshot().getJSONObject("config");
        control("move"); android.net.nsd.NsdManager.RegistrationListener announcement = advertise(paired.getString("serverId"), arguments.getString("reconnectEndpoint"), "Moved test desktop");
        try {
            JSONObject wrong = new JSONObject(paired.toString()).put("token", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            require(!DesktopConnection.reconnect(main, wrong, arguments.getString("reconnectEndpoint")), "A wrong key accepted the desktop identity proof.");
            require(paired.getString("endpoint").equals(PhoneStore.get(main).snapshot().getJSONObject("config").getString("endpoint")), "Rejected proof changed the saved address.");
            PhoneStore.get(main).endpoint("http://127.0.0.1:65533");
            require(!PhoneStore.get(main).reconnect(paired, arguments.getString("reconnectEndpoint")) && "http://127.0.0.1:65533".equals(PhoneStore.get(main).snapshot().getJSONObject("config").getString("endpoint")), "An older discovery result overwrote a newer manual address.");
            PhoneStore.get(main).endpoint(paired.getString("endpoint"));
            click(main, "Activity"); click(main, "Send pending / check saves");
            await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0, 30000, "Chosen desktop collection did not reconnect and save after its endpoint changed.");
            JSONObject reconnected = PhoneStore.get(main).snapshot().getJSONObject("config");
            require(arguments.getString("reconnectEndpoint").equals(reconnected.getString("endpoint")) && paired.getString("token").equals(reconnected.getString("token")) && paired.getString("deviceId").equals(reconnected.getString("deviceId")), "Automatic reconnect changed the pairing instead of its address.");
        } finally { ((android.net.nsd.NsdManager)main.getSystemService(Context.NSD_SERVICE)).unregisterService(announcement); }
        click(main, "Phone");
        click(main, "Desktop"); await(() -> collectionAvailable("Android chosen"), 10000, "Desktop did not reconnect."); click(main, "Android chosen");
        await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 1 && hasImage(main.getWindow().getDecorView()), 10000, "Chosen desktop board did not decode saved media."); alignedBoard(); screenshot(main, "remote.png");
        input("Search this collection", "absent reference"); await(() -> text(main).contains("No matching pins."), 10000, "Desktop search did not filter on the server."); input("Search this collection", "wide"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 1, 10000, "Desktop search did not find saved content."); input("Search this collection", ""); await(() -> collectionAvailable("Protected test · protected"), 10000, "Collection picker did not settle after search.");
        onMain(() -> { try { ((View)field(main, "collectionButton")).performClick(); } catch (Exception error) { throw new RuntimeException(error); } }); await(() -> has("Portrait frames"), 5000, "Collection dropdown did not open.");
        control("reorder-collections"); await(() -> "Portrait frames".equals(((org.json.JSONArray)field(main, "collectionChoices")).optJSONObject(0).optString("name")), 12000, "Collection refresh did not run while its dropdown was open.");
        click(main, "Portrait frames"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 4 && hasImage(main.getWindow().getDecorView()), 10000, "Dropdown changed the chosen collection after desktop rearrangement.");
        await(() -> { RecyclerView board = (RecyclerView)field(main, "pinGrid"); View card = board.getChildAt(0); return card != null && Math.abs((double)card.getWidth() / card.getHeight() - 90.0 / 160) < .02 && imageCount(board) == 4; }, 10000, "Desktop portrait frames were forced square or did not finish loading.");
        onMain(() -> require(imageFillsFrame(main.getWindow().getDecorView()), "Desktop whole-image fit prevented phone previews filling their tiles.")); alignedBoard(); screenshot(main, "portrait-board.png");
        click(main, "Many references"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 40, 10000, "First desktop page did not load.");
        scrollingControls(false);
        require("Fixture reference 0".equals(((org.json.JSONArray)field(main, "remotePins")).getJSONObject(0).optString("title")), "Desktop pins did not preserve the desktop's saved order.");
        click(main, "Load more pins"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 80, 10000, "Second desktop page did not load."); SystemClock.sleep(6000); require(((org.json.JSONArray)field(main, "remotePins")).length() == 80, "Refresh discarded loaded pages.");
        onMain(() -> { try { RecyclerView grid = (RecyclerView)field(main, "pinGrid"); require(grid.getChildCount() < 40 && ((GridLayoutManager)grid.getLayoutManager()).findFirstVisibleItemPosition() > 0, "Desktop cards were not recycled or refresh reset the scroll position."); } catch (Exception error) { throw new RuntimeException(error); } });
        onMain(() -> { try { ((RecyclerView)field(main, "pinGrid")).scrollToPosition(79); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> description(main.getWindow().getDecorView(), "Open pin: Fixture reference 79") != null, 5000, "Last loaded pin did not enter the board."); onMain(() -> description(main.getWindow().getDecorView(), "Open pin: Fixture reference 79").performClick());
        swipeViewer((PinViewer)field(main, "detailDialog"), 0, -220); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 85 && "Fixture reference 80".equals(((PinViewer)field(main, "detailDialog")).pin.optString("title")), 10000, "Viewer swipe did not fetch the next desktop page in desktop order."); click(main, "Close reference");
        control("reorder"); await(() -> "Fixture reference 84".equals(((org.json.JSONArray)field(main, "remotePins")).optJSONObject(0).optString("title")), 12000, "Foreground refresh did not follow desktop rearrangement.");
        org.json.JSONArray reordered = (org.json.JSONArray)field(main, "remotePins"); require(reordered.length() == 85, "Rearrangement discarded previously loaded pages."); for (int i = 1; i < 85; i++) require(("Fixture reference " + (i - 1)).equals(reordered.getJSONObject(i).optString("title")), "Rearrangement changed pagination order.");
        input("Search this collection", ""); input("Search this collection", "Fixture reference 83"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 1 && "Fixture reference 83".equals(((org.json.JSONArray)field(main, "remotePins")).getJSONObject(0).optString("title")), 10000, "Search could not find a reference outside loaded pages."); input("Search this collection", "");
        click(main, "Protected test · protected"); await(() -> has("Unlock"), 5000, "Protected collection did not prompt."); click(main, "Cancel"); require(!text(main).contains("Protected reference"), "Cancel revealed protected content.");
        clip(url + "?protected"); click(main, "Paste link"); click(main, "Desktop · Protected test · protected"); input("Collection password", "wrong"); click(main, "Unlock");
        await(() -> passwordError(), 10000, "Wrong password was not rejected."); input("Collection password", "mobile-test-password"); click(main, "Unlock");
        try { await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0 && text(main).contains("Protected reference") && ((org.json.JSONArray)field(main, "remotePins")).length() == 2, 20000, "Correct password did not save into and open the protected board."); }
        catch (AssertionError failure) { DesktopLibrary boardAccess = (DesktopLibrary)field(main, "desktop"); throw new AssertionError(failure.getMessage() + " App session matches delivery: " + boardAccess.sessionId().equals(DesktopConnection.sessionId()) + "; board authorization: " + protectedAuthorized(boardAccess) + "; delivery authorization: " + protectedAuthorized(new DesktopLibrary(main))); }
        await(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: Protected reference"); return card != null && hasImage(card); }, 10000, "Desktop did not deliver a decodable thumbnail for the protected video.");
        String protectedId = (String)field(main, "selectedRemote");
        click(main, "Another protected · protected"); await(() -> has("Unlock"), 5000, "Second protected collection did not request its initial password."); input("Collection password", "second-mobile-password"); click(main, "Unlock");
        await(() -> text(main).contains("Second private reference") && !has("Unlock"), 10000, "Second protected collection did not unlock.");
        for (String label : new String[] {"Protected test · protected", "Another protected · protected", "Protected test · protected"}) {
            click(main, label); String title = label.startsWith("Protected test") ? "Protected reference" : "Second private reference";
            await(() -> text(main).contains(title) || has("Unlock"), 10000, "Switching collections did not settle."); require(!has("Unlock") && text(main).contains(title), "Switching between unlocked collections prompted for the password again.");
        }
        await(() -> hasImage(description(main.getWindow().getDecorView(), "Open pin: Protected reference")), 10000, "Returning to the first protected collection lost its video cover.");
        View originalCard = description(main.getWindow().getDecorView(), "Open pin: Protected reference"); Bitmap originalThumbnail = imageBitmap(originalCard);
        onMain(() -> { try { ((RecyclerView)field(main, "pinGrid")).getAdapter().notifyDataSetChanged(); } catch (Exception error) { throw new RuntimeException(error); } }); waitForIdleSync();
        await(() -> imageBitmap(description(main.getWindow().getDecorView(), "Open pin: Protected reference")) == originalThumbnail, 3000, "Rebinding a scrolled card downloaded or decoded its thumbnail again.");
        MainActivity portrait = main; onMain(() -> display.resize(1440, 720, 320));
        await(() -> main != portrait && main.hasWindowFocus() && main.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && text(main).contains("Protected reference") && ((org.json.JSONArray)field(main, "remotePins")).length() == 2 && hasImage(main.getWindow().getDecorView()), 15000, "Rotation relocked the protected collection or lost its thumbnails.");
        require(portrait.isChangingConfigurations() && protectedId.equals(field(main, "selectedRemote")), "Rotation did not preserve the selected collection through configuration recreation.");
        MainActivity landscape = main; onMain(() -> display.resize(720, 1440, 320));
        await(() -> main != landscape && main.hasWindowFocus() && main.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT && text(main).contains("Protected reference") && hasImage(main.getWindow().getDecorView()), 15000, "Returning to portrait relocked the collection.");
        ActivityMonitor background = addMonitor(ScanActivity.class.getName(), null, false);
        try {
            MainActivity retained = main; onMain(() -> retained.startActivity(new Intent(retained, ScanActivity.class)));
            Activity covering = background.waitForActivityWithTimeout(10000); require(covering != null && covering.getDisplay().getDisplayId() == displayId, "Background fixture left the isolated display.");
            await(() -> !(Boolean)field(retained, "foreground"), 5000, "Papan did not enter its stopped/background state.");
            org.json.JSONArray whileStopped = new DesktopLibrary(retained).collections(); JSONObject authorized = null; for (int i = 0; i < whileStopped.length(); i++) if (protectedId.equals(whileStopped.getJSONObject(i).optString("id"))) authorized = whileStopped.getJSONObject(i);
            require(authorized != null && !authorized.optBoolean("locked"), "Switching away revoked the password before app close."); onMain(covering::finish);
            await(() -> main == retained && (Boolean)field(main, "foreground") && text(main).contains("Protected reference") && hasImage(main.getWindow().getDecorView()), 10000, "Returning from the background asked for the password.");
        } finally { removeMonitor(background); }
        MainActivity stopped = main; onMain(() -> stopped.finish()); await(stopped::isDestroyed, 5000, "Stopped protected activity did not close."); waitForIdleSync();
        main = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
        require(main.getDisplay().getDisplayId() == displayId, "Protected restart left the isolated display.");
        await(() -> main.hasWindowFocus() && !busy(), 10000, "Protected restart did not settle.");
        JSONObject afterStop = null; org.json.JSONArray afterStopCollections = new DesktopLibrary(main).collections();
        for (int i = 0; i < afterStopCollections.length(); i++) if (protectedId.equals(afterStopCollections.getJSONObject(i).getString("id"))) afterStop = afterStopCollections.getJSONObject(i);
        require(afterStop != null && afterStop.getBoolean("locked"), "Leaving the activity retained protected phone authorization.");
        for (int i = 0; i < afterStopCollections.length(); i++) require(!afterStopCollections.getJSONObject(i).optBoolean("protected") || afterStopCollections.getJSONObject(i).optBoolean("locked"), "App close retained another collection's grant.");
        click(main, "Desktop"); await(() -> text(main).contains("This collection is protected."), 10000, "Closed activity revealed protected contents after reopening.");
        click(main, "Unlock collection"); input("Collection password", "mobile-test-password"); click(main, "Unlock");
        await(() -> text(main).contains("Protected reference") && ((org.json.JSONArray)field(main, "remotePins")).length() == 2, 10000, "Protected board could not unlock after a real stop.");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: Protected reference"); require(card != null, "Protected card missing."); card.performClick(); });
        await(() -> viewerVideo() != null && viewerVideo().isPlaying(), 10000, "Protected desktop video did not play in place."); fittedVideo((PinViewer)field(main, "detailDialog")); screenshot(main, ((PinViewer)field(main, "detailDialog")).getWindow(), "remote-video-viewer.png");
        require(!has("Next") && !has("Previous"), "Attachment navigation buttons still cover the viewer.");
        swipeViewer((PinViewer)field(main, "detailDialog"), -220, 0); await(() -> viewerVideo() == null && has("Protected attachment fixture"), 5000, "Horizontal swipe did not advance the attachment."); swipeViewer((PinViewer)field(main, "detailDialog"), 220, 0); await(() -> viewerVideo() != null && viewerVideo().getCurrentPosition() > 250, 10000, "Horizontal swipe did not restore video."); click(main, "Pause");
        int pausedVideo = viewerVideo().getCurrentPosition(); MainActivity beforeViewerRotation = main; PinViewer beforeViewer = (PinViewer)field(main, "detailDialog"); onMain(() -> display.resize(1440, 720, 320));
        await(() -> main != beforeViewerRotation && field(main, "detailDialog") != null && field(main, "detailDialog") != beforeViewer && viewerVideo() != null && !viewerVideo().isPlaying() && Math.abs(viewerVideo().getCurrentPosition() - pausedVideo) < 200, 15000, "Viewer rotation lost its reference, position or paused state.");
        MainActivity wideViewer = main; onMain(() -> display.resize(720, 1440, 320)); await(() -> main != wideViewer && viewerVideo() != null && !viewerVideo().isPlaying() && Math.abs(viewerVideo().getCurrentPosition() - pausedVideo) < 200, 15000, "Portrait viewer restoration lost playback state.");
        control("lock"); await(() -> field(main, "detailDialog") == null && text(main).contains("This collection is protected."), 12000, "Desktop lock did not clear protected details.");
        android.util.LruCache<?, ?> cache = (android.util.LruCache<?, ?>)field(field(main, "previewImages"), "cache"); for (Object key : cache.snapshot().keySet()) require(!key.toString().startsWith("remote:" + protectedId + ":"), "Desktop lock retained protected thumbnail cache entries.");
        require(((PreviewImages)field(main, "previewImages")).ratios(protectedId).isEmpty(), "Desktop lock retained protected frame measurements.");
        require(((org.json.JSONArray)field(main, "remotePins")).length() == 0, "Locked remote pins remained in memory.");
        click(main, "Phone"); require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, "Remote work changed phone pins.");
    }
    private boolean passwordError() { boolean[] found = new boolean[1]; onMain(() -> { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { EditText value = inputView(window, "Collection password"); if (value != null && ((com.google.android.material.textfield.TextInputLayout)value.getTag()).getError() != null && ((com.google.android.material.textfield.TextInputLayout)value.getTag()).getError().toString().contains("Incorrect")) found[0] = true; } }); return found[0]; }
    private View find(View root, String label) {
        if (root.isShown() && (label.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription()) || root instanceof TextView && label.contentEquals(((TextView)root).getText()))) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { View found = find(((ViewGroup)root).getChildAt(i), label); if (found != null) return found; }
        return null;
    }
    private String text(Activity activity) { StringBuilder out = new StringBuilder(); onMain(() -> collect(activity.getWindow().getDecorView(), out)); return out.toString(); }
    private void collect(View root, StringBuilder out) { if (root instanceof TextView && root.isShown()) out.append(((TextView)root).getText()).append('\n'); if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) collect(((ViewGroup)root).getChildAt(i), out); }
    private Object field(Object target, String name) throws Exception { Field value = target.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(target); }
    private boolean busy() throws Exception { return ((java.util.concurrent.atomic.AtomicBoolean)field(main, "syncing")).get(); }
    private void screenshot(Activity activity, String name) throws Exception {
        screenshot(activity, activity.getWindow(), name);
    }
    private void screenshot(Activity activity, android.view.Window window, String name) throws Exception {
        require(activity.getDisplay().getDisplayId() == displayId, "Refusing capture outside the virtual display.");
        waitForIdleSync();
        View view = window.getDecorView(); require(view.getDisplay().getDisplayId() == displayId, "Dialog capture left the virtual display."); Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        java.util.concurrent.atomic.AtomicInteger copied = new java.util.concurrent.atomic.AtomicInteger(-1);
        try {
            onMain(() -> PixelCopy.request(window, bitmap, copied::set, new Handler(Looper.getMainLooper())));
            await(() -> copied.get() != -1, 5000, "Virtual window capture timed out."); require(copied.get() == PixelCopy.SUCCESS, "Virtual window capture failed.");
            android.widget.VideoView playing = videoView(view);
            if (playing != null && playing.getWidth() > 0 && playing.getHeight() > 0) {
                Bitmap frame = Bitmap.createBitmap(playing.getWidth(), playing.getHeight(), Bitmap.Config.ARGB_8888); java.util.concurrent.atomic.AtomicInteger surface = new java.util.concurrent.atomic.AtomicInteger(-1);
                try { await(() -> { surface.set(-1); onMain(() -> PixelCopy.request(playing, frame, surface::set, new Handler(Looper.getMainLooper()))); await(() -> surface.get() != -1, 2000, "Video surface capture timed out."); if (surface.get() == PixelCopy.ERROR_SOURCE_NO_DATA) return false; require(surface.get() == PixelCopy.SUCCESS, "Video surface copy failed with status " + surface.get()); return true; }, 5000, "Video surface did not deliver a decoded frame."); int pixel = frame.getPixel(frame.getWidth() / 2, frame.getHeight() / 2); require(android.graphics.Color.red(pixel) > 180 && android.graphics.Color.green(pixel) < 60 && android.graphics.Color.blue(pixel) < 60, "Native video displayed black or corrupted fixture pixels."); int[] location = new int[2]; playing.getLocationInWindow(location); new android.graphics.Canvas(bitmap).drawBitmap(frame, location[0], location[1], null); } finally { frame.recycle(); }
            }
            try (FileOutputStream file = new FileOutputStream(new File(activity.getFilesDir(), name))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, file); }
        } finally { bitmap.recycle(); }
    }
    private android.widget.VideoView videoView(View view) { if (view instanceof android.widget.VideoView) return (android.widget.VideoView)view; if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) { android.widget.VideoView video = videoView(((ViewGroup)view).getChildAt(i)); if (video != null) return video; } return null; }
    private int imageCount(View view) { int count = view instanceof ImageView && view.getTag() instanceof TextView && ((ImageView)view).getDrawable() instanceof android.graphics.drawable.BitmapDrawable ? 1 : 0; if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) count += imageCount(((ViewGroup)view).getChildAt(i)); return count; }
    private void onMain(Runnable action) { Throwable[] failure = new Throwable[1]; runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } }); if (failure[0] != null) throw new RuntimeException(failure[0]); }
    private interface Check { boolean run() throws Exception; }
    private void await(Check check, long timeout, String message) throws Exception { long end = SystemClock.elapsedRealtime() + timeout; while (SystemClock.elapsedRealtime() < end) { if (check.run()) return; SystemClock.sleep(100); } throw new AssertionError(message); }
    private void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
