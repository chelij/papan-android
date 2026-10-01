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
import android.widget.Button;
import android.widget.TextView;
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
            output = ImageReader.newInstance(720, 1440, PixelFormat.RGBA_8888, 2);
            output.setOnImageAvailableListener(reader -> { try (Image frame = reader.acquireLatestImage()) {} }, new Handler(Looper.getMainLooper()));
            // AOSP DisplayManager flags: own content, destroy on removal, trusted,
            // own unlocked group/focus, no feedback, and never steal physical-display focus.
            getUiAutomation().adoptShellPermissionIdentity();
            try {
                display = ((DisplayManager)getTargetContext().getSystemService(Context.DISPLAY_SERVICE)).createVirtualDisplay(
                    "Papan isolated UI test", 720, 1440, 320, output.getSurface(),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | (1 << 8) | (1 << 10) | (1 << 11) | (1 << 12) | (1 << 13) | (1 << 14) | (1 << 16));
            } finally { getUiAutomation().dropShellPermissionIdentity(); }
            require(display != null && "Papan isolated UI test".equals(display.getDisplay().getName()), "An isolated virtual display is required.");
            displayId = display.getDisplay().getDisplayId(); require(displayId != Display.DEFAULT_DISPLAY, "Refusing the physical display.");
            result.putInt("displayId", displayId);
            main = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
            require(main.getDisplay().getDisplayId() == displayId, "Activity opened on the wrong display.");
            await(() -> main.hasWindowFocus() && !busy(), 10000, "Main activity did not become ready on the virtual display.");
            if (!cameraOnly) { clipboard = (ClipboardManager)main.getSystemService(Context.CLIPBOARD_SERVICE); onMain(() -> previous = clipboard.getPrimaryClip()); clip("Keep this " + url); click(main, "Paste link"); }
            else onMain(() -> main.onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Keep this " + url)));
            await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 1, 3000, "Paste did not queue the clipboard URL: " + ((TextView)field(main, "status")).getText());
            require(url.equals(PhoneStore.get(main).snapshot().getJSONArray("entries").getJSONObject(0).getString("url")), "Wrong clipboard URL.");
            if (!cameraOnly) click(main, "Paste link");
            else onMain(() -> main.onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)));
            require(PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 1, "Repeated paste duplicated the pending URL.");
            screenshot(main, "unpaired.png");
            await(() -> !busy(), 3000, "Queue remained busy.");
            ActivityMonitor monitor = addMonitor(ScanActivity.class.getName(), null, false);
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
            require(((View)field(main, "setup")).getVisibility() == View.GONE, "Setup did not collapse after pairing.");
            await(() -> PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0 && text(main).contains("Saved in Papan."), 20000, "Foreground delivery did not observe the completed save.");
            screenshot(main, "paired.png");
            JSONObject before = PhoneStore.get(main).snapshot();
            if (!cameraOnly) { clip("no link here"); click(main, "Paste link"); require(text(main).contains("No web link was found") && PhoneStore.get(main).snapshot().getJSONArray("entries").length() == 0, "Invalid clipboard contents queued a link."); }
            require(before.getJSONObject("config").toString().equals(PhoneStore.get(main).snapshot().getJSONObject("config").toString()), "Clipboard paste changed the pairing.");
            result.putString("checks", "Virtual-display UI; " + (cameraOnly ? "share intent queues and deduplicates (clipboard skipped); " : "Paste link reads clipboard and deduplicates; ") + "Pair desktop immediately opens Camera2 and receives frames; invalid pairing stays in scanner; QR decoding at all four rotations; QR result pairs actual receiver; queued link saves and foreground status updates; pairing preserved.");
            result.putString("status", "passed");
        } catch (Throwable error) { result.putString("status", "failed"); result.putString("error", error.toString()); if (main != null) try { result.putString("screenStatus", ((TextView)field(main, "status")).getText().toString()); result.putBoolean("paired", PhoneStore.get(main).snapshot().has("config")); } catch (Exception ignored) {} }
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
        onMain(() -> { View view = find(activity.getWindow().getDecorView(), label); require(view instanceof Button && view.isShown(), "Missing visible button: " + label); view.performClick(); });
    }
    private View find(View root, String label) {
        if (root instanceof Button && label.contentEquals(((Button)root).getText())) return root;
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
