package com.papan.share;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.Locale;

final class PinViewer extends Dialog {
    interface Images { View frame(JSONObject item, boolean thumbnail); }
    interface Navigate { boolean move(int delta); }
    interface Actions { void populate(android.view.Menu menu); void select(int id); }
    final JSONObject pin;
    private final JSONArray items;
    private final Images images;
    private final DesktopLibrary desktop;
    private final File localFile;
    private final Actions actions;
    private MaterialToolbar toolbar;
    private final Navigate navigate;
    private final Handler handler = new Handler();
    private LinearLayout root, controls;
    private FrameLayout stage;
    private TextView status, time, page;
    private MaterialButton play;
    private Slider seek;
    private VideoView video;
    private View poster;
    private int index, position, mediaWidth, mediaHeight;
    private boolean prepared, playing, seeking;
    private float downX, downY;
    private boolean swipe;
    private final Runnable tick = new Runnable() { public void run() { updateProgress(); if (isShowing() && video != null) handler.postDelayed(this, 300); } };

    PinViewer(Context context, JSONObject pin, JSONArray items, Images images, DesktopLibrary desktop, File localFile, Actions actions, Navigate navigate, String itemId, int position, boolean playing) {
        super(context, R.style.PapanTheme); this.pin = pin; this.items = items; this.images = images; this.desktop = desktop; this.localFile = localFile; this.actions = actions; this.navigate = navigate; this.position = position; this.playing = playing;
        String selected = itemId == null ? pin.optString("coverId") : itemId;
        for (int i = 0; i < items.length(); i++) if (selected.equals(items.optJSONObject(i).optString("id"))) index = i;
    }
    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private TextView label(String value, int size) { TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(getContext().getColor(R.color.papan_on_surface)); return view; }
    private MaterialButton action(String value, Runnable action) {
        MaterialButton button = new MaterialButton(new ContextThemeWrapper(getContext(), R.style.PapanTextButtonOverlay)); button.setText(value); button.setAllCaps(false); button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48)); button.setOnClickListener(v -> action.run()); return button;
    }
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        root = column(); root.setBackgroundColor(getContext().getColor(R.color.papan_background)); setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> { Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return WindowInsetsCompat.CONSUMED; });
        toolbar = new MaterialToolbar(getContext()); toolbar.setTitle(pin.optString("title")); toolbar.setTitleTextAppearance(getContext(), com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        try { String host = new java.net.URI(pin.optString("sourceUrl")).getHost(); if (host != null) toolbar.setSubtitle(host.replaceFirst("^www\\.", "")); } catch (Exception ignored) {}
        toolbar.setNavigationIcon(R.drawable.close); toolbar.setNavigationIconTint(getContext().getColor(R.color.papan_on_surface)); toolbar.setNavigationContentDescription("Close reference"); toolbar.setNavigationOnClickListener(v -> dismiss());
        actions.populate(toolbar.getMenu());
        toolbar.setOnMenuItemClickListener(item -> { pause(); int id = item.getItemId(); toolbar.dismissPopupMenus(); handler.post(() -> { if (isShowing()) actions.select(id); }); return true; }); root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        stage = new FrameLayout(getContext()); stage.setContentDescription("Reference media. Swipe up or down for other pins; swipe sideways for this pin's attachments."); root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1)); stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> fitVideo());
        stage.setFocusable(true); stage.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setScrollable(true); info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD); info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
                if (items.length() > 1) { info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT); info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT); }
            }
            @Override public boolean performAccessibilityAction(View host, int action, Bundle arguments) {
                if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { movePin(action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1); return true; }
                if (items.length() > 1 && (action == android.R.id.accessibilityActionScrollLeft || action == android.R.id.accessibilityActionScrollRight)) { showItem((index + (action == android.R.id.accessibilityActionScrollRight ? 1 : items.length() - 1)) % items.length(), false); return true; }
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
        stage.setOnKeyListener((view, key, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN || key == android.view.KeyEvent.KEYCODE_DPAD_UP) { movePin(key == android.view.KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1); return true; }
            if (items.length() > 1 && (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) { showItem((index + (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : items.length() - 1)) % items.length(), false); return true; }
            return false;
        });
        status = label("", 15); status.setBackgroundColor(getContext().getColor(R.color.papan_background)); status.setGravity(Gravity.CENTER); status.setPadding(dp(20), dp(12), dp(20), dp(12)); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        controls = column(); controls.setPadding(dp(12), 0, dp(12), dp(8)); root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        if (items.length() > 1) {
            page = label("", 14); page.setGravity(Gravity.CENTER); page.setPadding(0, dp(8), 0, dp(8)); root.addView(page);
        }
        showItem(index, true);
    }
    @Override public void show() { super.show(); getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT); }
    private void showItem(int selected, boolean restore) {
        stopVideo(); index = selected; if (!restore) { position = 0; playing = true; } stage.removeAllViews(); controls.removeAllViews();
        if (page != null) page.setText((index + 1) + " / " + items.length());
        JSONObject item = items.optJSONObject(index);
        if (item == null || "text".equals(item.optString("kind"))) {
            android.widget.ScrollView scroll = new android.widget.ScrollView(getContext()); TextView text = label(item == null ? pin.optString("text", pin.optString("notes")) : item.optString("text"), 17); text.setPadding(dp(24), dp(16), dp(24), dp(24)); text.setTextIsSelectable(true); scroll.addView(text); stage.addView(scroll, new FrameLayout.LayoutParams(-1, -1)); return;
        }
        if ("image".equals(item.optString("kind"))) { stage.addView(images.frame(item, false), new FrameLayout.LayoutParams(-1, -1)); return; }
        if (!"video".equals(item.optString("kind"))) { status.setText("No saved preview. Open the pin menu to view the original."); stage.addView(status, new FrameLayout.LayoutParams(-1, -1)); return; }
        mediaWidth = item.optInt("width"); mediaHeight = item.optInt("height");
        poster = desktop == null || item.has("thumbnailUrl") ? images.frame(item, true) : null;
        if (poster != null) stage.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        video = new VideoView(getContext()); video.setContentDescription("Video"); stage.addView(video, 0, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        status.setText("Loading video…"); stage.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        seek = new Slider(getContext()); seek.setContentDescription("Video position"); seek.setValueFrom(0); seek.setValueTo(1); seek.setTrackHeight(dp(2)); seek.setThumbRadius(dp(6)); seek.setHaloRadius(dp(16)); seek.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE); seek.setEnabled(false); controls.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
        seek.addOnSliderTouchListener(new Slider.OnSliderTouchListener() { public void onStartTrackingTouch(Slider slider) { seeking = true; } public void onStopTrackingTouch(Slider slider) { seeking = false; seekTo(slider.getValue()); } });
        seek.addOnChangeListener((slider, value, fromUser) -> { if (fromUser && !seeking) seekTo(value); });
        LinearLayout transport = new LinearLayout(getContext()); transport.setGravity(Gravity.CENTER_VERTICAL); controls.addView(transport);
        play = action(playing ? "Pause" : "Play", () -> { if (!prepared) { playing = true; play.setText("Pause"); startVideo(item); return; } if (video.isPlaying()) pause(); else { if (video.getCurrentPosition() >= video.getDuration()) video.seekTo(0); video.start(); playing = true; play.setText("Pause"); } });
        play.setContentDescription(playing ? "Pause video" : "Play video"); time = label("0:00 / 0:00", 14); time.setGravity(Gravity.END); transport.addView(play); transport.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        startVideo(item);
    }
    private void startVideo(JSONObject item) {
        try {
            final VideoView active = video;
            prepared = false; status.setVisibility(View.VISIBLE); status.setText("Loading video…");
            video.setOnPreparedListener(player -> { if (video != active) return; prepared = true; player.setLooping(true); mediaWidth = player.getVideoWidth(); mediaHeight = player.getVideoHeight(); fitVideo(); seek.setEnabled(true); if (position > 0) video.seekTo(position); if (playing) video.start(); else { video.seekTo(Math.max(1, position)); status.setVisibility(View.GONE); } updateProgress(); handler.removeCallbacks(tick); handler.post(tick); });
            video.setOnInfoListener((player, what, extra) -> { if (video != active) return false; if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START || what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) { status.setVisibility(View.GONE); if (poster != null) poster.setVisibility(View.GONE); } else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) { status.setText("Loading video…"); status.setVisibility(View.VISIBLE); } return false; });
            video.setOnCompletionListener(player -> { if (video != active) return; playing = false; play.setText("Play"); play.setContentDescription("Play video"); updateProgress(); });
            video.setOnErrorListener((player, what, extra) -> { if (video != active) return true; prepared = false; playing = false; seek.setEnabled(false); status.setVisibility(View.VISIBLE); status.setText("This video cannot be played. Retry or open the original from the pin menu."); play.setText("Retry"); play.setContentDescription("Retry video"); return true; });
            if (desktop != null) { String url = item.optString("originalUrl", item.optString("previewUrl")); video.setVideoURI(Uri.parse(url), desktop.videoHeaders(url)); }
            else if (localFile != null) video.setVideoURI(Uri.fromFile(localFile)); else throw new Exception("Saved video is unavailable.");
        } catch (Exception error) { status.setText(error.getMessage()); play.setText("Retry"); play.setContentDescription("Retry video"); }
    }
    private void fitVideo() {
        if (video == null || mediaWidth <= 0 || mediaHeight <= 0 || stage.getWidth() <= 0 || stage.getHeight() <= 0) return;
        double scale = Math.min((double)stage.getWidth() / mediaWidth, (double)stage.getHeight() / mediaHeight);
        FrameLayout.LayoutParams size = (FrameLayout.LayoutParams)video.getLayoutParams(); int width = Math.max(1, (int)Math.round(mediaWidth * scale)), height = Math.max(1, (int)Math.round(mediaHeight * scale));
        if (size.width != width || size.height != height) { size.width = width; size.height = height; size.gravity = Gravity.CENTER; video.setLayoutParams(size); }
    }
    private void seekTo(float fraction) { if (prepared && video != null) { position = Math.round(fraction * video.getDuration()); video.seekTo(position); updateProgress(); } }
    private void updateProgress() {
        if (!prepared || video == null) return; int duration = video.getDuration(), current = video.getCurrentPosition();
        if (!seeking && duration > 0) seek.setValue(Math.max(0, Math.min(1, (float)current / duration)));
        time.setText(clock(current) + " / " + clock(duration)); play.setContentDescription(video.isPlaying() ? "Pause video" : "Play video");
    }
    private String clock(int milliseconds) { int seconds = Math.max(0, milliseconds / 1000); return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    private boolean zoomed(View view) { if (view instanceof ZoomImageView && ((ZoomImageView)view).zoomed()) return true; if (view instanceof android.view.ViewGroup) { android.view.ViewGroup group = (android.view.ViewGroup)view; for (int i = 0; i < group.getChildCount(); i++) if (zoomed(group.getChildAt(i))) return true; } return false; }
    void notice(String message) { com.google.android.material.snackbar.Snackbar.make(root, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show(); }
    private void movePin(int delta) { if (!navigate.move(delta)) notice(delta > 0 ? "End of this collection." : "First pin in this collection."); }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (stage != null) {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) { int[] location = new int[2]; stage.getLocationOnScreen(location); downX = event.getRawX(); downY = event.getRawY(); swipe = downY >= location[1] && downY < location[1] + stage.getHeight() && !zoomed(stage); }
            if (event.getPointerCount() > 1 || action == android.view.MotionEvent.ACTION_CANCEL) swipe = false;
            if (action == android.view.MotionEvent.ACTION_UP && swipe) {
                float x = event.getRawX() - downX, y = event.getRawY() - downY; swipe = false;
                if (Math.abs(y) > dp(64) && Math.abs(y) > Math.abs(x) * 1.5) { movePin(y < 0 ? 1 : -1); return true; }
                if (items.length() > 1 && Math.abs(x) > dp(64) && Math.abs(x) > Math.abs(y) * 1.5) { showItem((index + (x < 0 ? 1 : items.length() - 1)) % items.length(), false); return true; }
                if (prepared && video != null && Math.abs(x) < dp(12) && Math.abs(y) < dp(12)) { if (video.isPlaying()) pause(); else { video.start(); playing = true; play.setText("Pause"); } return true; }
            }
        }
        return super.dispatchTouchEvent(event);
    }
    void pause() { if (video != null && prepared) { position = video.getCurrentPosition(); video.pause(); playing = false; play.setText("Play"); play.setContentDescription("Play video"); } }
    String itemId() { JSONObject item = items.optJSONObject(index); return item == null ? "" : item.optString("id"); }
    int position() { return video != null && prepared ? video.getCurrentPosition() : position; }
    boolean playing() { return video != null && prepared ? video.isPlaying() : playing; }
    private void stopVideo() { handler.removeCallbacks(tick); if (video != null) video.stopPlayback(); video = null; prepared = false; }
    @Override public void dismiss() { if (toolbar != null) toolbar.dismissPopupMenus(); stopVideo(); super.dismiss(); }
}
