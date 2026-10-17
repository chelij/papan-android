package com.papan.share;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import java.io.File;

final class VideoPreview extends TextureView implements TextureView.SurfaceTextureListener {
    private final File file;
    private final DesktopLibrary desktop;
    private final String url;
    private final boolean crop;
    private MediaPlayer player;
    private boolean active, prepared, failed;
    private int position, mediaWidth, mediaHeight;
    private final Runnable prepareTimeout = () -> { if (player != null && !prepared) fail(); };

    VideoPreview(Context context, File file, boolean crop) { this(context, file, null, null, crop); }
    VideoPreview(Context context, DesktopLibrary desktop, String url, boolean crop) { this(context, null, desktop, url, crop); }
    private VideoPreview(Context context, File file, DesktopLibrary desktop, String url, boolean crop) {
        super(context); this.file = file; this.desktop = desktop; this.url = url; this.crop = crop;
        setOpaque(false); setAlpha(0); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); setSurfaceTextureListener(this);
    }
    void setActive(boolean value) { if (value && !active) failed = false; active = value; if (active) start(); else release(); }
    private void start() {
        if (player != null || failed || !active || !isAvailable() || desktop == null && (file == null || !file.isFile())) return;
        try {
            MediaPlayer next = new MediaPlayer(); player = next;
            next.setVolume(0, 0); next.setLooping(true);
            next.setOnPreparedListener(ready -> { if (player != ready) return; removeCallbacks(prepareTimeout); prepared = true; mediaWidth = ready.getVideoWidth(); mediaHeight = ready.getVideoHeight(); fit(); if (position > 0) ready.seekTo(position); if (active) ready.start(); });
            next.setOnVideoSizeChangedListener((ready, width, height) -> { if (player == ready) { mediaWidth = width; mediaHeight = height; fit(); } });
            next.setOnInfoListener((ready, what, extra) -> { if (player == ready && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) setAlpha(1); return false; });
            next.setOnErrorListener((ready, what, extra) -> { if (player == ready) fail(); return true; });
            if (desktop == null) next.setDataSource(file.toString()); else next.setDataSource(getContext(), Uri.parse(url), desktop.videoHeaders(url));
            Surface surface = new Surface(getSurfaceTexture()); try { next.setSurface(surface); } finally { surface.release(); }
            next.prepareAsync(); postDelayed(prepareTimeout, 15000);
        } catch (Exception ignored) { fail(); }
    }
    private void fail() { failed = true; release(); }
    private void fit() {
        if (mediaWidth <= 0 || mediaHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        float scale = crop ? Math.max((float)getWidth() / mediaWidth, (float)getHeight() / mediaHeight) : Math.min((float)getWidth() / mediaWidth, (float)getHeight() / mediaHeight);
        Matrix matrix = new Matrix(); matrix.setScale(mediaWidth * scale / getWidth(), mediaHeight * scale / getHeight(), getWidth() / 2f, getHeight() / 2f); setTransform(matrix);
    }
    private void release() {
        removeCallbacks(prepareTimeout);
        MediaPlayer previous = player; player = null;
        if (previous != null) { if (prepared) try { position = previous.getCurrentPosition(); } catch (IllegalStateException ignored) {} previous.release(); }
        prepared = false; setAlpha(0);
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) { super.onSizeChanged(width, height, oldWidth, oldHeight); fit(); }
    @Override protected void onDetachedFromWindow() { setActive(false); super.onDetachedFromWindow(); }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { start(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { fit(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { release(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
