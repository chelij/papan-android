package com.papan.share;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;

final class MediaChooser extends BottomSheetDialog {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final LinearLayout content;
    private final MaterialButton next;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private JSONObject inspection;
    MediaChooser(Context context, String url, java.util.function.BiConsumer<JSONObject, JSONArray> complete, Runnable linkOnly, Runnable closed) {
        super(context);
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16), dp(20), dp(16), dp(16));
        TextView heading = label("Choose media", 22); heading.setPadding(0, 0, 0, dp(12)); body.addView(heading);
        ScrollView scroll = new ScrollView(context); content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL); content.addView(label("Looking for images and videos…", 16)); scroll.addView(content); body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        next = new MaterialButton(context); next.setText("Choose collection"); next.setEnabled(false); next.setOnClickListener(v -> { JSONArray keys = new JSONArray(); for (String key : selected) keys.put(key); JSONObject plan = inspection; dismiss(); complete.accept(plan, keys); }); body.addView(next, new LinearLayout.LayoutParams(-1, -2));
        MaterialButton only = new MaterialButton(new android.view.ContextThemeWrapper(context, R.style.PapanTextButtonOverlay)); only.setText("Save link only / send to desktop"); only.setOnClickListener(v -> { dismiss(); linkOnly.run(); }); body.addView(only, new LinearLayout.LayoutParams(-1, -2));
        MaterialButton cancel = new MaterialButton(new android.view.ContextThemeWrapper(context, R.style.PapanTextButtonOverlay)); cancel.setText("Cancel"); cancel.setOnClickListener(v -> dismiss()); body.addView(cancel, new LinearLayout.LayoutParams(-1, -2));
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        if (metrics.heightPixels / metrics.density < 420 || context.getResources().getConfiguration().fontScale >= 1.5f) { scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); ScrollView entire = new ScrollView(context); entire.addView(body); setContentView(entire); }
        else { setContentView(body); body.getLayoutParams().height = Math.min(metrics.heightPixels - dp(64), dp(640)); }
        setOnDismissListener(dialog -> { cancelled.set(true); worker.shutdownNow(); content.removeAllViews(); inspection = null; selected.clear(); closed.run(); });
        show(); body.requestLayout();
        com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior = getBehavior(); behavior.setSkipCollapsed(true); behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        inspect(url);
    }
    private void inspect(String url) {
        content.removeAllViews(); content.addView(label("Looking for images and videos…", 16)); next.setEnabled(false);
        worker.execute(() -> {
            try {
                JSONObject result = (JSONObject)MediaExtractor.run(getContext(), new JSONObject().put("action", "inspect").put("url", url), cancelled);
                next.post(() -> { if (!cancelled.get()) render(result); });
            } catch (Exception error) { next.post(() -> { if (!cancelled.get()) { content.removeAllViews(); content.addView(label("Media could not be loaded. " + error.getMessage(), 16)); MaterialButton retry = new MaterialButton(getContext()); retry.setText("Retry"); retry.setOnClickListener(v -> inspect(url)); content.addView(retry); } }); }
        });
    }
    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size) { TextView label = new TextView(getContext()); label.setText(value); label.setTextSize(size); label.setTextColor(getContext().getColor(R.color.papan_on_surface)); return label; }
    private void render(JSONObject value) {
        inspection = value; content.removeAllViews(); TextView title = label(value.optString("title"), 16); title.setMaxLines(3); title.setPadding(0, 0, 0, dp(12)); content.addView(title);
        JSONArray items = value.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i); if (item == null || !("image".equals(item.optString("kind")) || "video".equals(item.optString("kind")))) continue;
            String key = item.optString("key"), name = ("video".equals(item.optString("kind")) ? "Video " : "Image ") + (i + 1);
            MaterialCardView card = new MaterialCardView(getContext()); card.setRadius(dp(12)); card.setStrokeWidth(dp(1)); card.setCardElevation(0); card.setCheckable(true); card.setCheckedIcon(null); card.setFocusable(true); card.setContentDescription(name + ", not selected");
            LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8), dp(8), dp(12), dp(8));
            ImageView image = new ImageView(getContext()); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setContentDescription(name + " preview"); row.addView(image, new LinearLayout.LayoutParams(dp(112), dp(112)));
            TextView kind = label(name, 16); kind.setPadding(dp(12), 0, 0, 0); row.addView(kind, new LinearLayout.LayoutParams(0, -2, 1)); card.addView(row);
            card.setOnClickListener(v -> { boolean checked = !selected.contains(key); if (checked) selected.add(key); else selected.remove(key); card.setChecked(checked); card.setCardBackgroundColor(getContext().getColor(checked ? R.color.papan_primary_container : R.color.papan_surface)); card.setStrokeColor(getContext().getColor(checked ? R.color.papan_primary : R.color.papan_outline)); card.setContentDescription(name + (checked ? ", selected" : ", not selected")); next.setEnabled(!selected.isEmpty()); next.setText("Choose collection (" + selected.size() + ")"); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(8); content.addView(card, params);
            String poster = item.optString("poster", "image".equals(item.optString("kind")) ? item.optString("url") : "");
            if (!poster.isEmpty() && !poster.equals("null")) worker.execute(() -> { try { byte[] bytes = LocalCapture.preview(poster); BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options); options.inSampleSize = 1; while (options.outWidth / options.inSampleSize > 256 || options.outHeight / options.inSampleSize > 256) options.inSampleSize *= 2; options.inJustDecodeBounds = false; android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options); image.post(() -> { if (!cancelled.get()) image.setImageBitmap(bitmap); else if (bitmap != null) bitmap.recycle(); }); } catch (Exception ignored) {} });
        }
    }
}
