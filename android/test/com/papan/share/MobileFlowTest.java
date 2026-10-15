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
    @Override public Activity newActivity(ClassLoader loader, String name, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException { Activity activity = super.newActivity(loader, name, intent); if (activity instanceof MainActivity) { android.content.res.Configuration config = new android.content.res.Configuration(); config.fontScale = testFontScale; activity.applyOverrideConfiguration(config); } return activity; }
    @Override public void callActivityOnCreate(Activity activity, Bundle saved) { super.callActivityOnCreate(activity, saved); if (activity instanceof MainActivity) main = (MainActivity)activity; }
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
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
                clip(url); click(main, "Paste link"); click(main, "On phone · Pocket");
                await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 1 && "saved".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(0).optString("capture")), 15000, "Standalone phone image was not saved.");
                await(() -> hasImage(main.getWindow().getDecorView()), 5000, "Phone board did not decode its saved image.");
                clip(url); click(main, "Paste link"); click(main, "On phone · Pocket");
                require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 1, "Repeated phone paste duplicated a pin.");
                clip(url); click(main, "Paste link"); click(main, "On phone · Study");
                await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, 3000, "Same URL could not be saved in a different phone collection.");
                JSONObject local = MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(1);
                await(() -> "saved".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(1).optString("capture")), 15000, "Second phone preview was not saved.");
                onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: " + local.optString("title")); require(card != null, "Phone pin card missing."); card.performClick(); });
                click(main, "Details"); click(main, "Edit title and notes"); input("Title", "Pocket reference"); input("Notes", "Saved without desktop"); click(main, "Save changes");
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
            result.putString("checks", "Virtual-display UI; " + (cameraOnly ? "share intent queues and deduplicates (clipboard skipped); " : "native phone collection creation; local image capture/decoded preview; per-collection paste/deduplication; pin edit/search; private storage reopen; Paste link reads clipboard and chooses destination; ") + "Scan QR opens Camera2 and receives frames; invalid pairing stays in scanner; QR decoding at all four rotations; QR result pairs actual receiver; queued link saves and foreground status updates; pairing preserved; open desktop boards; selected destination retained across offline storage/activity restart; newest-first desktop pagination preserved on refresh and search beyond loaded pages; protected password/cancel/wrong/correct access; protected video thumbnails decode from desktop; rotation preserves the unlocked collection and thumbnails in both orientations; backgrounding retains password access; an actual app close/reopen clears authorization; desktop lock clears contents and cached protected thumbnails.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Explore viewing: full-screen fitted local/remote videos autoplay with verified red decoded surface pixels; local video covers decode; play/pause and keyboard seeking work; vertical swipes move between pins and fetch more desktop pages; attachment buttons and horizontal swipes work; viewer rotation restores its reference, position and paused state; portrait desktop frame proportions remain aligned; a rebound remote thumbnail reuses the same decoded bitmap.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Native DNS-SD nearby desktop discovery; phone-initiated request and displayed six-digit code; desktop approval pairs without QR; authenticated automatic endpoint change preserves the pairing token, request ID and chosen destination; wrong-key identity proof is rejected.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Direct MP4 capture/native playback/deletion; collection rename/delete/cancel and form validation; add/share sheet cancellation; failed capture/retry preserves the link; source browser intent; pairing/address recovery controls; inline accessible Connections feedback, repeated pending-link warning and preserved destinations/key; no snackbar behind a modal, deferred board feedback after dismissal; keyboard focus; 300dp phone, 640dp tablet and 200% font reflow; Material bottom navigation/rail and Add link meet 48dp touch targets and remain inside the isolated window.");
            if (!cameraOnly && new File(getTargetContext().getFilesDir(), "style-demo.json").exists()) result.putString("checks", result.getString("checks") + " Credited artwork board: equal card bounds and aligned rows/columns at three preview sizes; real slider keyboard input; collection-specific sizing persists across fresh activity; fill/whole-image control; light/dark/system appearance controls; native screenshots with decoded real artworks.");
            if (!cameraOnly) { result.putDouble("captionTitleContrast", captionTitleContrast); result.putDouble("captionSourceContrast", captionSourceContrast); result.putString("checks", result.getString("checks") + " Desktop-style captions: single-line title/source, no solid caption block, continuous rendered fade with AA contrast at every text row over pure white, full labels retained for details/accessibility."); }
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
    private void click(Activity activity, String label) {
        require(activity.getDisplay().getDisplayId() == displayId, "Refusing interaction outside the test display.");
        if (label.equals("Load more pins")) onMain(() -> { try { RecyclerView grid = (RecyclerView)field(main, "pinGrid"); grid.scrollToPosition(grid.getAdapter().getItemCount() - 1); } catch (Exception error) { throw new RuntimeException(error); } });
        try { await(() -> has(label), 1000, "Control still transitioning."); } catch (Exception | AssertionError ignored) {}
        if (!has(label) && activity == main && main.hasWindowFocus()) onMain(() -> { try { com.google.android.material.appbar.MaterialToolbar toolbar = (com.google.android.material.appbar.MaterialToolbar)field(main, "toolbar"); for (int i = 0; i < toolbar.getMenu().size(); i++) { android.view.MenuItem item = toolbar.getMenu().getItem(i); if (item.isVisible() && label.contentEquals(item.getTitle())) { toolbar.showOverflowMenu(); break; } } } catch (Exception error) { throw new RuntimeException(error); } });
        boolean[] clicked = new boolean[1];
        try { await(() -> { onMain(() -> {
            View view = findWindow(label); if (view == null || !view.isShown()) return;
            require(view.getDisplay().getDisplayId() == displayId, "Wrong window display.");
            view.requestRectangleOnScreen(new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight()));
            while (!view.isClickable() && view.getParent() instanceof View) {
                if (view.getParent() instanceof android.widget.AdapterView) { android.widget.AdapterView<?> list = (android.widget.AdapterView<?>)view.getParent(); int position = list.getPositionForView(view); require(position >= 0, "Menu item has no position."); list.performItemClick(view, position, list.getItemIdAtPosition(position)); clicked[0] = true; return; }
                view = (View)view.getParent();
            }
            require(view.isClickable(), "Control is not clickable: " + label); view.performClick(); clicked[0] = true;
        }); return clicked[0]; }, 5000, "Missing focused control: " + label); } catch (Exception error) { throw new RuntimeException(error); }
    }
    private View findWindow(String label) { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { View found = find(window, label); if (found != null && found.isShown()) return found; } return null; }
    private boolean has(String label) { boolean[] result = new boolean[1]; onMain(() -> result[0] = findWindow(label) != null); return result[0]; }
    private void input(String hint, String value) { if (hint.equals("Search this collection")) { try { if (!((View)field(main, "searchLayout")).isShown()) click(main, "Search"); } catch (Exception error) { throw new RuntimeException(error); } } try { await(() -> { boolean[] found = new boolean[1]; onMain(() -> { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId && inputView(window, hint) != null) found[0] = true; }); return found[0]; }, 5000, "Missing focused input: " + hint); } catch (Exception error) { throw new RuntimeException(error); } onMain(() -> { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { EditText input = inputView(window, hint); if (input != null) { input.setText(value); return; } } throw new AssertionError("Missing input: " + hint); }); }
    private EditText inputView(View root, String hint) { if (root instanceof EditText && hint.contentEquals(((EditText)root).getHint())) return (EditText)root; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { EditText found = inputView(((ViewGroup)root).getChildAt(i), hint); if (found != null) return found; } return null; }
    private View description(View root, String label) { if (label.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { View found = description(((ViewGroup)root).getChildAt(i), label); if (found != null) return found; } return null; }
    private boolean hasImage(View root) { if (root instanceof ImageView && root.getTag() instanceof TextView && ((ImageView)root).getDrawable() instanceof android.graphics.drawable.BitmapDrawable) return true; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) if (hasImage(((ViewGroup)root).getChildAt(i))) return true; return false; }
    private Bitmap imageBitmap(View root) { if (root instanceof ImageView && root.getTag() instanceof TextView && ((ImageView)root).getDrawable() instanceof android.graphics.drawable.BitmapDrawable) return ((android.graphics.drawable.BitmapDrawable)((ImageView)root).getDrawable()).getBitmap(); if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { Bitmap bitmap = imageBitmap(((ViewGroup)root).getChildAt(i)); if (bitmap != null) return bitmap; } return null; }
    private void localVideo(String imageUrl) throws Exception {
        String url = imageUrl.replace("/wide.png", "/portrait.mp4"); clip(url); click(main, "Paste link"); click(main, "On phone · Pocket");
        await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 3 && "saved".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2).optString("capture")), 15000, "Direct video was not captured on phone.");
        JSONObject pin = MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2); File media = MobileLibrary.get(main).mediaFile(pin.getString("mediaFile")); require(media.isFile(), "Local video bytes missing.");
        await(() -> has("Open pin: " + url), 5000, "Captured video did not appear on the phone board.");
        await(() -> imageBitmap(description(main.getWindow().getDecorView(), "Open pin: " + url)) != null, 10000, "Local video did not get a decoded frame cover."); alignedBoard(); screenshot(main, "video-board.png");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: " + url); require(card != null, "Video pin missing."); card.performClick(); });
        await(() -> viewerVideo() != null && viewerVideo().getCurrentPosition() > 200, 10000, "Saved phone video did not autoplay in the viewer.");
        PinViewer viewer = (PinViewer)field(main, "detailDialog"); fittedVideo(viewer); screenshot(main, viewer.getWindow(), "video-viewer.png");
        click(main, "Pause"); int paused = viewerVideo().getCurrentPosition(); SystemClock.sleep(300); require(!viewerVideo().isPlaying() && Math.abs(viewerVideo().getCurrentPosition() - paused) < 100, "Pause did not stop playback.");
        onMain(() -> { try { com.google.android.material.slider.Slider slider = (com.google.android.material.slider.Slider)field(viewer, "seek"); slider.requestFocus(); slider.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)); slider.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> viewerVideo().getCurrentPosition() > paused + 50, 5000, "Native video seek control did not seek.");
        click(main, "Play"); await(() -> viewerVideo().isPlaying(), 5000, "Play did not resume playback.");
        swipeViewer(viewer, 0, -220); await(() -> field(main, "detailDialog") != viewer && viewerVideo() == null, 5000, "Swipe up did not move to the next pin.");
        swipeViewer((PinViewer)field(main, "detailDialog"), 0, -220); await(() -> has("End of this collection."), 3000, "Collection boundary feedback was hidden behind the viewer.");
        swipeViewer((PinViewer)field(main, "detailDialog"), 0, 220); await(() -> viewerVideo() != null && viewerVideo().isPlaying(), 10000, "Swipe down did not return to the video.");
        click(main, "Details"); click(main, "Delete from phone"); click(main, "Delete"); await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2 && !media.exists(), 5000, "Phone delete did not remove saved media.");
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
            click(main, "On phone");
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
        click(main, "On phone · Study"); require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, "Share intent did not retain per-collection deduplication.");
        click(main, "Pocket");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: " + url); require(card != null, "Phone reference missing."); card.performClick(); });
        android.content.IntentFilter links = new android.content.IntentFilter(Intent.ACTION_VIEW); links.addDataScheme("http"); links.addDataScheme("https");
        ActivityMonitor source = addMonitor(links, new ActivityResult(Activity.RESULT_CANCELED, null), true);
        click(main, "Details"); try { click(main, "Open source"); require(source.getHits() == 1, "Source did not route to a browser intent."); } finally { removeMonitor(source); }
        click(main, "Save to another collection"); click(main, "On phone · Study");
        require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, "Saving to an existing destination duplicated the reference.");
        click(main, "New collection"); input("Collection name", "Temporary check"); click(main, "Create");
        click(main, "Delete collection"); click(main, "Keep");
        require(MobileLibrary.get(main).snapshot().getJSONArray("collections").length() == 3, "Delete cancellation removed the collection.");
        String failedUrl = url.replace("/wide.png", "/missing");
        clip(failedUrl); click(main, "Paste link"); click(main, "On phone · Temporary check");
        await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 3 && "failed".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2).optString("capture")), 10000, "Failed capture did not retain its link.");
        await(() -> has("Open pin: " + failedUrl) && text(main).contains("Preview unavailable"), 5000, "Failed preview did not expose its status and link."); alignedBoard(); screenshot(main, "failed-board.png");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: " + failedUrl); require(card != null, "Failed reference missing."); card.performClick(); });
        click(main, "Details"); click(main, "Retry preview");
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
        relaunch(1280, 1600, 1); require(grid().getSpanCount() == 4, "Tablet did not use four columns."); controlsFit(); await(() -> hasImage(main.getWindow().getDecorView()), 5000, "Tablet preview did not decode."); alignedBoard(); screenshot(main, "tablet.png");
        relaunch(720, 1600, 2); require(main.getResources().getConfiguration().fontScale == 2 && grid().getSpanCount() == 1, "Large font layout did not reflow."); controlsFit(); alignedBoard(); screenshot(main, "large-text.png");
    }
    private void controlsFit() throws Exception { onMain(() -> { try {
        View root = (View)field(main, "root"), fab = (View)field(main, "addLink");
        com.google.android.material.navigation.NavigationBarView navigation = (com.google.android.material.navigation.NavigationBarView)field(main, "navigation");
        boolean wide = main.getResources().getConfiguration().screenWidthDp >= 600;
        require(wide ? navigation instanceof com.google.android.material.navigationrail.NavigationRailView : navigation instanceof com.google.android.material.bottomnavigation.BottomNavigationView, "Navigation did not adapt to window width.");
        for (View control : new View[] {fab, navigation.findViewById(1), navigation.findViewById(2), navigation.findViewById(3)}) {
            int[] point = new int[2]; control.getLocationOnScreen(point); require(control.getWidth() >= 96 && control.getHeight() >= 96 && point[0] >= 0 && point[0] + control.getWidth() <= root.getWidth() && point[1] + control.getHeight() <= root.getHeight(), "Material navigation or Add link clipped or below 48dp touch target.");
        }
        require(((View)field(main, "pinGrid")).getHeight() > 0, "Navigation consumed the canvas.");
    } catch (Exception error) { throw new RuntimeException(error); } }); }
    private void control(String action) throws Exception { Delivery.request(arguments.getString("controlEndpoint"), "/" + action, "", new JSONObject()); }
    private void remoteBoards(String url) throws Exception {
        click(main, "Desktop"); await(() -> has("Android chosen"), 10000, "Open desktop collections did not load.");
        clip(url + "?chosen"); click(main, "Paste link"); await(() -> has("Desktop · Android chosen"), 10000, "Desktop destination picker did not load.");
        control("disable"); click(main, "Desktop · Android chosen");
        await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 1 && !busy(), 15000, "Offline desktop choice was not retained.");
        JSONObject pending = PhoneStore.get(main).snapshot().getJSONArray("entries").getJSONObject(0); require(pending.has("collectionId") && "Android chosen".equals(pending.optString("collectionName")), "Selected collection was lost.");
        String requestId = pending.getString("id"), destination = pending.getString("collectionId");
        onMain(() -> main.finish()); waitForIdleSync(); Field singleton = PhoneStore.class.getDeclaredField("instance"); singleton.setAccessible(true); singleton.set(null, null);
        main = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
        require(main.getDisplay().getDisplayId() == displayId, "Restart opened on physical display."); await(() -> main.hasWindowFocus() && !busy(), 15000, "Restart did not settle.");
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
        click(main, "On phone");
        click(main, "Desktop"); await(() -> has("Android chosen"), 10000, "Desktop did not reconnect."); click(main, "Android chosen");
        await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 1 && hasImage(main.getWindow().getDecorView()), 10000, "Chosen desktop board did not decode saved media."); alignedBoard(); screenshot(main, "remote.png");
        input("Search this collection", "absent reference"); await(() -> text(main).contains("No matching pins."), 10000, "Desktop search did not filter on the server."); input("Search this collection", "wide"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 1, 10000, "Desktop search did not find saved content."); input("Search this collection", ""); await(() -> has("Protected test · protected"), 10000, "Collection tabs did not settle after search.");
        click(main, "Portrait frames"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 4 && hasImage(main.getWindow().getDecorView()), 10000, "Portrait desktop previews did not load.");
        await(() -> { RecyclerView board = (RecyclerView)field(main, "pinGrid"); View card = board.getChildAt(0); return card != null && Math.abs((double)card.getWidth() / card.getHeight() - 90.0 / 160) < .02 && imageCount(board) == 4; }, 10000, "Desktop portrait frames were forced square or did not finish loading."); alignedBoard(); screenshot(main, "portrait-board.png");
        click(main, "Many references"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 40, 10000, "First desktop page did not load.");
        require("Fixture reference 84".equals(((org.json.JSONArray)field(main, "remotePins")).getJSONObject(0).optString("title")), "Desktop pins were not newest first.");
        click(main, "Load more pins"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 80, 10000, "Second desktop page did not load."); SystemClock.sleep(6000); require(((org.json.JSONArray)field(main, "remotePins")).length() == 80, "Refresh discarded loaded pages.");
        onMain(() -> { try { RecyclerView grid = (RecyclerView)field(main, "pinGrid"); require(grid.getChildCount() < 40 && ((GridLayoutManager)grid.getLayoutManager()).findFirstVisibleItemPosition() > 0, "Desktop cards were not recycled or refresh reset the scroll position."); } catch (Exception error) { throw new RuntimeException(error); } });
        onMain(() -> { try { ((RecyclerView)field(main, "pinGrid")).scrollToPosition(79); } catch (Exception error) { throw new RuntimeException(error); } });
        await(() -> description(main.getWindow().getDecorView(), "Open pin: Fixture reference 5") != null, 5000, "Last loaded pin did not enter the board."); onMain(() -> description(main.getWindow().getDecorView(), "Open pin: Fixture reference 5").performClick());
        swipeViewer((PinViewer)field(main, "detailDialog"), 0, -220); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 85 && "Fixture reference 4".equals(((PinViewer)field(main, "detailDialog")).pin.optString("title")), 10000, "Viewer swipe did not fetch the next desktop page."); click(main, "Close reference");
        input("Search this collection", "Fixture reference 0"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 1 && "Fixture reference 0".equals(((org.json.JSONArray)field(main, "remotePins")).getJSONObject(0).optString("title")), 10000, "Search could not find a reference outside loaded pages."); input("Search this collection", "");
        click(main, "Protected test · protected"); await(() -> has("Unlock"), 5000, "Protected collection did not prompt."); click(main, "Cancel"); require(!text(main).contains("Protected reference"), "Cancel revealed protected content.");
        clip(url + "?protected"); click(main, "Paste link"); click(main, "Desktop · Protected test · protected"); input("Collection password", "wrong"); click(main, "Unlock");
        await(() -> passwordError(), 10000, "Wrong password was not rejected."); input("Collection password", "mobile-test-password"); click(main, "Unlock");
        await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0 && text(main).contains("Protected reference") && ((org.json.JSONArray)field(main, "remotePins")).length() == 2, 20000, "Correct password did not save into and open the protected board.");
        await(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: Protected reference"); return card != null && hasImage(card); }, 10000, "Desktop did not deliver a decodable thumbnail for the protected video.");
        String protectedId = (String)field(main, "selectedRemote");
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
        click(main, "Desktop"); await(() -> text(main).contains("This collection is protected."), 10000, "Closed activity revealed protected contents after reopening.");
        click(main, "Unlock collection"); input("Collection password", "mobile-test-password"); click(main, "Unlock");
        await(() -> text(main).contains("Protected reference") && ((org.json.JSONArray)field(main, "remotePins")).length() == 2, 10000, "Protected board could not unlock after a real stop.");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: Protected reference"); require(card != null, "Protected card missing."); card.performClick(); });
        await(() -> viewerVideo() != null && viewerVideo().isPlaying(), 10000, "Protected desktop video did not play in place."); fittedVideo((PinViewer)field(main, "detailDialog")); screenshot(main, ((PinViewer)field(main, "detailDialog")).getWindow(), "remote-video-viewer.png");
        click(main, "Next"); require(viewerVideo() == null && has("Protected attachment fixture"), "Attachment paging did not leave the video."); click(main, "Previous"); await(() -> viewerVideo() != null && viewerVideo().isPlaying(), 10000, "Previous attachment did not resume in-place video.");
        swipeViewer((PinViewer)field(main, "detailDialog"), -220, 0); await(() -> viewerVideo() == null && has("Protected attachment fixture"), 5000, "Horizontal swipe did not advance the attachment."); swipeViewer((PinViewer)field(main, "detailDialog"), 220, 0); await(() -> viewerVideo() != null && viewerVideo().getCurrentPosition() > 250, 10000, "Horizontal swipe did not restore video."); click(main, "Pause");
        int pausedVideo = viewerVideo().getCurrentPosition(); MainActivity beforeViewerRotation = main; PinViewer beforeViewer = (PinViewer)field(main, "detailDialog"); onMain(() -> display.resize(1440, 720, 320));
        await(() -> main != beforeViewerRotation && field(main, "detailDialog") != null && field(main, "detailDialog") != beforeViewer && viewerVideo() != null && !viewerVideo().isPlaying() && Math.abs(viewerVideo().getCurrentPosition() - pausedVideo) < 200, 15000, "Viewer rotation lost its reference, position or paused state.");
        MainActivity wideViewer = main; onMain(() -> display.resize(720, 1440, 320)); await(() -> main != wideViewer && viewerVideo() != null && !viewerVideo().isPlaying() && Math.abs(viewerVideo().getCurrentPosition() - pausedVideo) < 200, 15000, "Portrait viewer restoration lost playback state.");
        control("lock"); await(() -> field(main, "detailDialog") == null && text(main).contains("This collection is protected."), 12000, "Desktop lock did not clear protected details.");
        android.util.LruCache<?, ?> cache = (android.util.LruCache<?, ?>)field(field(main, "previewImages"), "cache"); for (Object key : cache.snapshot().keySet()) require(!key.toString().startsWith("remote:" + protectedId + ":"), "Desktop lock retained protected thumbnail cache entries.");
        require(((PreviewImages)field(main, "previewImages")).ratios(protectedId).isEmpty(), "Desktop lock retained protected frame measurements.");
        require(((org.json.JSONArray)field(main, "remotePins")).length() == 0, "Locked remote pins remained in memory.");
        click(main, "On phone"); require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, "Remote work changed phone pins.");
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
                try { onMain(() -> PixelCopy.request(playing, frame, surface::set, new Handler(Looper.getMainLooper()))); await(() -> surface.get() != -1, 5000, "Video surface capture timed out."); require(surface.get() == PixelCopy.SUCCESS, "Video surface has no decoded frame."); int pixel = frame.getPixel(frame.getWidth() / 2, frame.getHeight() / 2); require(android.graphics.Color.red(pixel) > 180 && android.graphics.Color.green(pixel) < 60 && android.graphics.Color.blue(pixel) < 60, "Native video displayed black or corrupted fixture pixels."); int[] location = new int[2]; playing.getLocationInWindow(location); new android.graphics.Canvas(bitmap).drawBitmap(frame, location[0], location[1], null); } finally { frame.recycle(); }
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
