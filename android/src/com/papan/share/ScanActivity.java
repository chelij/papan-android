package com.papan.share;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanActivity extends Activity {
    private TextureView preview;
    private TextView status;
    private HandlerThread thread;
    private Handler worker;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private Surface surface;
    private Size size;
    private int sensorRotation;
    private int autofocus;
    private long lastFrame;
    private boolean permissionRequested;
    private volatile boolean resumed, opening;
    private final Semaphore cameraLock = new Semaphore(1);
    private final AtomicBoolean found = new AtomicBoolean(false);

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        permissionRequested = saved != null && saved.getBoolean("permissionRequested");
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding); setContentView(body);
        body.setOnApplyWindowInsetsListener((view, insets) -> { view.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(), padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom()); return insets; });
        TextView title = new TextView(this); title.setText("Pair your desktop"); title.setTextSize(25); body.addView(title);
        TextView hint = new TextView(this); hint.setText("Point the camera at the QR in Papan’s Receive from phone window."); hint.setTextSize(15); hint.setPadding(0, padding / 2, 0, padding); body.addView(hint);
        preview = new TextureView(this); preview.setContentDescription("Pairing QR camera preview");
        body.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this); status.setTextSize(14); status.setPadding(0, padding / 2, 0, padding / 2); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(status);
        Button paste = new Button(this); paste.setText("Paste pairing link instead"); paste.setAllCaps(false);
        paste.setOnClickListener(view -> {
            ClipData clip = ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).getText() == null) status.setText("Copy the complete pairing link from Papan first.");
            else complete(clip.getItemAt(0).getText().toString());
        }); body.addView(paste);
        Button cancel = new Button(this); cancel.setText("Cancel"); cancel.setAllCaps(false); cancel.setOnClickListener(view -> finish()); body.addView(cancel);
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { openCamera(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { transformPreview(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { closeCamera(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
        });
    }

    @Override public void onResume() {
        super.onResume(); resumed = true;
        thread = new HandlerThread("papan-qr-camera"); thread.start(); worker = new Handler(thread.getLooper());
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionRequested) { permissionRequested = true; requestPermissions(new String[]{Manifest.permission.CAMERA}, 1); }
            else status.setText("Camera access is off. You can paste the pairing link instead.");
        }
        else if (preview.isAvailable()) openCamera();
    }
    @Override public void onSaveInstanceState(Bundle state) { state.putBoolean("permissionRequested", permissionRequested); super.onSaveInstanceState(state); }
    @Override public void onPause() {
        resumed = false; closeCamera();
        if (thread != null) { thread.quitSafely(); try { thread.join(1000); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } thread = null; worker = null; }
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 1) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) { if (preview.isAvailable()) openCamera(); }
        else status.setText("Camera access is off. You can paste the pairing link instead.");
    }

    private void openCamera() {
        if (!resumed || worker == null || !preview.isAvailable() || camera != null || opening || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || !cameraLock.tryAcquire()) return;
        opening = true;
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String chosen = null;
            for (String id : manager.getCameraIdList()) if (Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING))) { chosen = id; break; }
            if (chosen == null) throw new Exception("No rear camera is available. Paste the pairing link instead.");
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(chosen);
            sensorRotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            autofocus = CaptureRequest.CONTROL_AF_MODE_OFF;
            for (int mode : characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)) if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) autofocus = mode;
            StreamConfigurationMap streams = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] previews = streams.getOutputSizes(SurfaceTexture.class); size = null;
            for (Size option : streams.getOutputSizes(ImageFormat.YUV_420_888)) if (option.getWidth() <= 1280 && option.getHeight() <= 1280 && Arrays.asList(previews).contains(option) && (size == null || option.getWidth() * option.getHeight() > size.getWidth() * size.getHeight())) size = option;
            if (size == null) throw new Exception("This camera cannot provide a QR preview. Paste the pairing link instead.");
            runOnUiThread(this::transformPreview);
            reader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(value -> {
                try (Image image = value.acquireLatestImage()) {
                    if (image == null || found.get() || !resumed || SystemClock.elapsedRealtime() - lastFrame < 250) return;
                    lastFrame = SystemClock.elapsedRealtime();
                    Image.Plane plane = image.getPlanes()[0]; ByteBuffer source = plane.getBuffer();
                    int width = image.getWidth(), height = image.getHeight(), base = source.position();
                    byte[] pixels = new byte[width * height];
                    for (int row = 0; row < height; row++) {
                        if (plane.getPixelStride() == 1) { source.position(base + row * plane.getRowStride()); source.get(pixels, row * width, width); }
                        else for (int column = 0; column < width; column++) pixels[row * width + column] = source.get(base + row * plane.getRowStride() + column * plane.getPixelStride());
                    }
                    String link = decode(pixels, width, height);
                    runOnUiThread(() -> complete(link));
                } catch (ReaderException ignored) { /* Keep scanning; frames are never stored. */ }
                catch (IllegalStateException ignored) { /* The camera was closed during this frame. */ }
            }, worker);
            preview.getSurfaceTexture().setDefaultBufferSize(size.getWidth(), size.getHeight());
            surface = new Surface(preview.getSurfaceTexture());
            manager.openCamera(chosen, new CameraDevice.StateCallback() {
                private void opened() { if (opening) { opening = false; cameraLock.release(); } }
                @Override public void onOpened(CameraDevice device) {
                    camera = device; opened();
                    if (!resumed) { closeCamera(); return; }
                    try {
                        device.createCaptureSession(Arrays.asList(surface, reader.getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession capture) {
                                if (!resumed || camera != device) { capture.close(); return; }
                                session = capture;
                                try {
                                    CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                    request.addTarget(surface); request.addTarget(reader.getSurface());
                                    request.set(CaptureRequest.CONTROL_AF_MODE, autofocus);
                                    session.setRepeatingRequest(request.build(), null, worker);
                                    runOnUiThread(() -> status.setText("Looking for Papan’s pairing QR…"));
                                } catch (Exception error) { unavailable(); }
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession capture) { capture.close(); unavailable(); }
                        }, worker);
                    } catch (Exception error) { unavailable(); }
                }
                @Override public void onDisconnected(CameraDevice device) { device.close(); if (camera == device) camera = null; opened(); unavailable(); }
                @Override public void onError(CameraDevice device, int error) { device.close(); if (camera == device) camera = null; opened(); unavailable(); }
            }, worker);
        } catch (Exception error) {
            opening = false; cameraLock.release();
            status.setText(error.getMessage() == null ? "Camera unavailable. Paste the pairing link instead." : error.getMessage());
            closeCamera();
        }
    }

    private void unavailable() { runOnUiThread(() -> status.setText("Camera unavailable. Paste the pairing link instead.")); }
    private void closeCamera() {
        boolean acquired = false;
        try {
            acquired = cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
            if (!acquired) return;
            if (session != null) { session.close(); session = null; }
            if (camera != null) { camera.close(); camera = null; }
            if (reader != null) { reader.close(); reader = null; }
            if (surface != null) { surface.release(); surface = null; }
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        finally { if (acquired) cameraLock.release(); }
    }
    private void transformPreview() {
        if (size == null || preview.getWidth() == 0 || preview.getHeight() == 0) return;
        int rotation = (sensorRotation - getWindowManager().getDefaultDisplay().getRotation() * 90 + 360) % 360;
        boolean turn = rotation == 90 || rotation == 270;
        float scale = Math.max(preview.getWidth() / (float)(turn ? size.getHeight() : size.getWidth()), preview.getHeight() / (float)(turn ? size.getWidth() : size.getHeight()));
        Matrix matrix = new Matrix();
        matrix.setScale(size.getWidth() / (float)preview.getWidth(), size.getHeight() / (float)preview.getHeight());
        matrix.postTranslate(-size.getWidth() / 2f, -size.getHeight() / 2f); matrix.postRotate(rotation);
        matrix.postScale(scale, scale); matrix.postTranslate(preview.getWidth() / 2f, preview.getHeight() / 2f);
        preview.setTransform(matrix);
    }
    static String decode(byte[] pixels, int width, int height) throws ReaderException {
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(pixels, width, height, 0, 0, width, height, false);
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)), Collections.singletonMap(DecodeHintType.TRY_HARDER, Boolean.TRUE)).getText();
    }
    private void complete(String value) {
        if (!resumed || found.get()) return;
        try {
            if (value == null || value.length() > 8192) throw new Exception();
            Uri uri = Uri.parse(value.trim());
            String code, endpoint;
            if ("papan-pair".equals(uri.getScheme()) && "connect".equals(uri.getHost())) { endpoint = uri.getQueryParameter("endpoint"); code = uri.getQueryParameter("code"); }
            else if ("http".equals(uri.getScheme()) && "/pair".equals(uri.getPath())) { endpoint = "http://" + uri.getAuthority(); code = uri.getFragment(); }
            else throw new Exception();
            Delivery.endpoint(endpoint == null ? "" : endpoint);
            if (code == null || !code.matches("[a-f0-9]{32}")) throw new Exception();
            if (!found.compareAndSet(false, true)) return;
            setResult(RESULT_OK, new Intent().putExtra("pairingLink", value.trim())); finish();
        } catch (Exception error) { status.setText("This isn’t a Papan pairing link. Use the QR or complete pairing link from Receive from phone."); }
    }
}
