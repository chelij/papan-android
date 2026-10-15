package com.papan.share;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

final class ZoomImageView extends ImageView {
    private final Matrix transform = new Matrix();
    private float zoom = 1;
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;
    ZoomImageView(Context context) {
        super(context); setScaleType(ScaleType.MATRIX); setFocusable(true);
        scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) { zoomTo(Math.max(1, Math.min(5, zoom * detector.getScaleFactor())), detector.getFocusX(), detector.getFocusY()); return true; }
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onDoubleTap(MotionEvent event) { if (zoom > 1) fit(); else zoomTo(2.5f, event.getX(), event.getY()); return true; }
            @Override public boolean onScroll(MotionEvent first, MotionEvent last, float x, float y) { if (zoom <= 1 || scale.isInProgress()) return false; transform.postTranslate(-x, -y); constrain(); return true; }
        });
        setContentDescription("Reference image. Pinch or double tap to zoom.");
    }
    @Override public void setImageBitmap(Bitmap bitmap) { super.setImageBitmap(bitmap); fit(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); fit(); }
    boolean zoomed() { return zoom > 1.01f; }
    private void fit() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
        int width = getDrawable().getIntrinsicWidth(), height = getDrawable().getIntrinsicHeight(); if (width <= 0 || height <= 0) return;
        zoom = 1; float ratio = Math.min((float)getWidth() / width, (float)getHeight() / height);
        transform.setScale(ratio, ratio); transform.postTranslate((getWidth() - width * ratio) / 2, (getHeight() - height * ratio) / 2); setImageMatrix(transform);
    }
    private void zoomTo(float value, float x, float y) { if (getDrawable() == null) return; transform.postScale(value / zoom, value / zoom, x, y); zoom = value; constrain(); }
    private void constrain() {
        RectF rect = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight()); transform.mapRect(rect);
        float x = rect.width() <= getWidth() ? (getWidth() - rect.width()) / 2 - rect.left : rect.left > 0 ? -rect.left : rect.right < getWidth() ? getWidth() - rect.right : 0;
        float y = rect.height() <= getHeight() ? (getHeight() - rect.height()) / 2 - rect.top : rect.top > 0 ? -rect.top : rect.bottom < getHeight() ? getHeight() - rect.bottom : 0;
        transform.postTranslate(x, y); setImageMatrix(transform);
    }
    @Override public boolean onTouchEvent(MotionEvent event) { scale.onTouchEvent(event); gestures.onTouchEvent(event); if (zoomed() || event.getPointerCount() > 1) getParent().requestDisallowInterceptTouchEvent(true); if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick(); return true; }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean onKeyDown(int key, android.view.KeyEvent event) {
        if (key == android.view.KeyEvent.KEYCODE_ENTER || key == android.view.KeyEvent.KEYCODE_DPAD_CENTER) { if (zoomed()) fit(); else zoomTo(2.5f, getWidth() / 2f, getHeight() / 2f); return true; }
        if (zoomed() && key >= android.view.KeyEvent.KEYCODE_DPAD_UP && key <= android.view.KeyEvent.KEYCODE_DPAD_RIGHT) { int step = Math.round(32 * getResources().getDisplayMetrics().density); transform.postTranslate(key == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? step : key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? -step : 0, key == android.view.KeyEvent.KEYCODE_DPAD_UP ? step : key == android.view.KeyEvent.KEYCODE_DPAD_DOWN ? -step : 0); constrain(); return true; }
        return super.onKeyDown(key, event);
    }
}
