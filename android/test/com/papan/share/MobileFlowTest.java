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
    private Bundle arguments;
    private MainActivity main;
    private ScanActivity scan;
    private VirtualDisplay display;
    private ImageReader output;
    private ClipboardManager clipboard;
    private ClipData previous;
    private String clipboardFixture;
    private int displayId;
    private float testFontScale = 1;
    @Override public Activity newActivity(ClassLoader loader, String name, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException { Activity activity = super.newActivity(loader, name, intent); if (activity instanceof MainActivity) { android.content.res.Configuration config = new android.content.res.Configuration(); config.fontScale = testFontScale; activity.applyOverrideConfiguration(config); } return activity; }
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
                click(main, "Edit title and notes"); input("Title", "Pocket reference"); input("Notes", "Saved without desktop"); click(main, "Save changes");
                input("Search this collection", "does not match"); require(text(main).contains("No matching pins."), "Phone collection search did not filter."); input("Search this collection", "Pocket reference"); require(text(main).contains("Pocket reference"), "Phone pin edit/search failed."); input("Search this collection", "");
                await(() -> hasImage(main.getWindow().getDecorView()), 5000, "Edited phone card did not decode its preview."); screenshot(main, "local.png");
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
                decoded = ScanActivity.decode(pixels, 480, 480);
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
            if (!cameraOnly && arguments.getString("controlEndpoint") != null) remoteBoards(url);
            if (!cameraOnly) { localVideo(url); collectionControls(url); responsive(); }
            result.putString("checks", "Virtual-display UI; " + (cameraOnly ? "share intent queues and deduplicates (clipboard skipped); " : "native phone collection creation; local image capture/decoded preview; per-collection paste/deduplication; pin edit/search; private storage reopen; Paste link reads clipboard and chooses destination; ") + "Pair desktop immediately opens Camera2 and receives frames; invalid pairing stays in scanner; QR decoding at all four rotations; QR result pairs actual receiver; queued link saves and foreground status updates; pairing preserved; open desktop boards; selected destination retained across offline storage/activity restart; newest-first desktop pagination preserved on refresh and search beyond loaded pages; protected password/cancel/wrong/correct access and desktop lock clear contents.");
            if (!cameraOnly) result.putString("checks", result.getString("checks") + " Direct MP4 capture/native playback/deletion; collection rename/delete/cancel and form validation; add/share sheet cancellation; failed capture/retry preserves the link; source browser intent; pairing/address recovery controls; keyboard focus; 300dp phone, 640dp tablet and 200% font reflow; Material bottom navigation/rail and Add link meet 48dp touch targets and remain inside the isolated window.");
            result.putString("status", "passed");
        } catch (Throwable error) { result.putString("status", "failed"); result.putString("error", error.toString()); if (main != null) try { screenshot(main, "failure.png"); result.putString("screenStatus", ((TextView)field(main, "status")).getText().toString()); result.putString("visibleText", text(main)); result.putBoolean("paired", PhoneStore.get(main).snapshot().has("config")); } catch (Exception ignored) {} }
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
        try { await(() -> has(label), 5000, "Missing focused control: " + label); } catch (Exception error) { throw new RuntimeException(error); }
        onMain(() -> {
            View view = findWindow(label); require(view != null && view.isShown(), "Missing visible control: " + label); require(view.getDisplay().getDisplayId() == displayId, "Wrong window display.");
            view.requestRectangleOnScreen(new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight()));
            while (!view.isClickable() && view.getParent() instanceof View) {
                if (view.getParent() instanceof android.widget.AdapterView) { android.widget.AdapterView<?> list = (android.widget.AdapterView<?>)view.getParent(); int position = list.getPositionForView(view); require(position >= 0, "Menu item has no position."); list.performItemClick(view, position, list.getItemIdAtPosition(position)); return; }
                view = (View)view.getParent();
            }
            require(view.isClickable(), "Control is not clickable: " + label); view.performClick();
        });
    }
    private View findWindow(String label) { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { View found = find(window, label); if (found != null && found.isShown()) return found; } return null; }
    private boolean has(String label) { boolean[] result = new boolean[1]; onMain(() -> result[0] = findWindow(label) != null); return result[0]; }
    private void input(String hint, String value) { try { await(() -> { boolean[] found = new boolean[1]; onMain(() -> { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId && inputView(window, hint) != null) found[0] = true; }); return found[0]; }, 5000, "Missing focused input: " + hint); } catch (Exception error) { throw new RuntimeException(error); } onMain(() -> { for (View window : WindowInspector.getGlobalWindowViews()) if (window.hasWindowFocus() && window.getDisplay() != null && window.getDisplay().getDisplayId() == displayId) { EditText input = inputView(window, hint); if (input != null) { input.setText(value); return; } } throw new AssertionError("Missing input: " + hint); }); }
    private EditText inputView(View root, String hint) { if (root instanceof EditText && hint.contentEquals(((EditText)root).getHint())) return (EditText)root; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { EditText found = inputView(((ViewGroup)root).getChildAt(i), hint); if (found != null) return found; } return null; }
    private View description(View root, String label) { if (label.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) { View found = description(((ViewGroup)root).getChildAt(i), label); if (found != null) return found; } return null; }
    private boolean hasImage(View root) { if (root instanceof ImageView && root.getTag() instanceof TextView && ((ImageView)root).getDrawable() instanceof android.graphics.drawable.BitmapDrawable) return true; if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)root).getChildCount(); i++) if (hasImage(((ViewGroup)root).getChildAt(i))) return true; return false; }
    private void localVideo(String imageUrl) throws Exception {
        String url = imageUrl.replace("/wide.png", "/portrait.mp4"); clip(url); click(main, "Paste link"); click(main, "On phone · Pocket");
        await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 3 && "saved".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2).optString("capture")), 15000, "Direct video was not captured on phone.");
        JSONObject pin = MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2); File media = MobileLibrary.get(main).mediaFile(pin.getString("mediaFile")); require(media.isFile(), "Local video bytes missing.");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: " + url); require(card != null, "Video pin missing."); card.performClick(); }); click(main, "Play video");
        await(() -> field(main, "video") != null && ((android.widget.VideoView)field(main, "video")).getCurrentPosition() > 200, 10000, "Saved phone video did not play.");
        click(main, "Delete from phone"); click(main, "Delete"); await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2 && !media.exists(), 5000, "Phone delete did not remove saved media.");
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
        try { click(main, "Open source"); require(source.getHits() == 1, "Source did not route to a browser intent."); } finally { removeMonitor(source); }
        click(main, "Save to another collection"); click(main, "On phone · Study");
        require(MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, "Saving to an existing destination duplicated the reference.");
        click(main, "New collection"); input("Collection name", "Temporary check"); click(main, "Create");
        click(main, "Delete collection"); click(main, "Keep");
        require(MobileLibrary.get(main).snapshot().getJSONArray("collections").length() == 3, "Delete cancellation removed the collection.");
        String failedUrl = url.replace("/wide.png", "/missing");
        clip(failedUrl); click(main, "Paste link"); click(main, "On phone · Temporary check");
        await(() -> MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 3 && "failed".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2).optString("capture")), 10000, "Failed capture did not retain its link.");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: " + failedUrl); require(card != null, "Failed reference missing."); card.performClick(); });
        click(main, "Retry preview");
        await(() -> "failed".equals(MobileLibrary.get(main).snapshot().getJSONArray("pins").getJSONObject(2).optString("capture")), 10000, "Capture retry did not settle.");
        click(main, "Delete collection"); click(main, "Delete");
        await(() -> MobileLibrary.get(main).snapshot().getJSONArray("collections").length() == 2 && MobileLibrary.get(main).snapshot().getJSONArray("pins").length() == 2, 5000, "Collection delete affected the wrong references.");
        click(main, "Connections"); input("Complete pairing link", "not a pairing link"); click(main, "Pair from link");
        await(() -> !((TextView)field(main, "status")).getText().toString().equals("Pairing…"), 5000, "Invalid manual pairing did not return an error.");
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
    private void responsive() throws Exception {
        relaunch(600, 1400, 1); require(grid().getSpanCount() == 1, "Narrow phone did not use one column."); controlsFit(); screenshot(main, "narrow.png");
        relaunch(1280, 1600, 1); require(grid().getSpanCount() == 3, "Tablet did not use three columns."); controlsFit(); await(() -> hasImage(main.getWindow().getDecorView()), 5000, "Tablet preview did not decode."); screenshot(main, "tablet.png");
        relaunch(720, 1600, 2); require(main.getResources().getConfiguration().fontScale == 2 && grid().getSpanCount() == 1, "Large font layout did not reflow."); controlsFit(); screenshot(main, "large-text.png");
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
        control("enable"); click(main, "Activity"); click(main, "Send pending / check saves");
        await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0, 20000, "Chosen desktop collection did not save after restart."); click(main, "On phone");
        click(main, "Desktop"); await(() -> has("Android chosen"), 10000, "Desktop did not reconnect."); click(main, "Android chosen");
        await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 1 && hasImage(main.getWindow().getDecorView()), 10000, "Chosen desktop board did not decode saved media."); screenshot(main, "remote.png");
        input("Search this collection", "absent reference"); await(() -> text(main).contains("No matching pins."), 10000, "Desktop search did not filter on the server."); input("Search this collection", "wide"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 1, 10000, "Desktop search did not find saved content."); input("Search this collection", ""); await(() -> has("Protected test · protected"), 10000, "Collection tabs did not settle after search.");
        click(main, "Many references"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 40, 10000, "First desktop page did not load.");
        require("Fixture reference 84".equals(((org.json.JSONArray)field(main, "remotePins")).getJSONObject(0).optString("title")), "Desktop pins were not newest first.");
        click(main, "Load more pins"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 80, 10000, "Second desktop page did not load."); SystemClock.sleep(6000); require(((org.json.JSONArray)field(main, "remotePins")).length() == 80, "Refresh discarded loaded pages.");
        onMain(() -> { try { RecyclerView grid = (RecyclerView)field(main, "pinGrid"); require(grid.getChildCount() < 40 && ((GridLayoutManager)grid.getLayoutManager()).findFirstVisibleItemPosition() > 0, "Desktop cards were not recycled or refresh reset the scroll position."); } catch (Exception error) { throw new RuntimeException(error); } });
        input("Search this collection", "Fixture reference 0"); await(() -> ((org.json.JSONArray)field(main, "remotePins")).length() == 1 && "Fixture reference 0".equals(((org.json.JSONArray)field(main, "remotePins")).getJSONObject(0).optString("title")), 10000, "Search could not find a reference outside loaded pages."); input("Search this collection", "");
        click(main, "Protected test · protected"); await(() -> has("Unlock"), 5000, "Protected collection did not prompt."); click(main, "Cancel"); require(!text(main).contains("Protected reference"), "Cancel revealed protected content.");
        clip(url + "?protected"); click(main, "Paste link"); click(main, "Desktop · Protected test · protected"); input("Collection password", "wrong"); click(main, "Unlock");
        await(() -> passwordError(), 10000, "Wrong password was not rejected."); input("Collection password", "mobile-test-password"); click(main, "Unlock");
        await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0 && text(main).contains("Protected reference") && ((org.json.JSONArray)field(main, "remotePins")).length() == 2, 20000, "Correct password did not save into and open the protected board.");
        onMain(() -> { View card = description(main.getWindow().getDecorView(), "Open pin: Protected reference"); require(card != null, "Protected card missing."); card.performClick(); });
        control("lock"); await(() -> field(main, "detailDialog") == null && text(main).contains("This collection is protected."), 12000, "Desktop lock did not clear protected details.");
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
        require(activity.getDisplay().getDisplayId() == displayId, "Refusing capture outside the virtual display.");
        waitForIdleSync();
        View view = activity.getWindow().getDecorView(); Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        java.util.concurrent.atomic.AtomicInteger copied = new java.util.concurrent.atomic.AtomicInteger(-1);
        try {
            onMain(() -> PixelCopy.request(activity.getWindow(), bitmap, copied::set, new Handler(Looper.getMainLooper())));
            await(() -> copied.get() != -1, 5000, "Virtual window capture timed out."); require(copied.get() == PixelCopy.SUCCESS, "Virtual window capture failed.");
            try (FileOutputStream file = new FileOutputStream(new File(activity.getFilesDir(), name))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, file); }
        } finally { bitmap.recycle(); }
    }
    private void onMain(Runnable action) { Throwable[] failure = new Throwable[1]; runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } }); if (failure[0] != null) throw new RuntimeException(failure[0]); }
    private interface Check { boolean run() throws Exception; }
    private void await(Check check, long timeout, String message) throws Exception { long end = SystemClock.elapsedRealtime() + timeout; while (SystemClock.elapsedRealtime() < end) { if (check.run()) return; SystemClock.sleep(100); } throw new AssertionError(message); }
    private void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
