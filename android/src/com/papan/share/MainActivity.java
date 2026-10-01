package com.papan.share;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private LinearLayout body, queue, setup;
    private TextView status, connection;
    private EditText pairLink, address;
    private Button settingsButton, manualPairButton, addressButton;
    private boolean settingsOpen = false, foreground = false;
    private String sharedId, pendingPair;
    private long pollUntil;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::sync;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20), dp(16), dp(20), dp(24)); scroll.addView(body); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> { view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        TextView title = label("papan.", 32); title.setPadding(0, 0, 0, 12);
        label("Share a link to Papan. Your paired desktop extracts and saves it to its chosen collection.", 15);
        status = label("", 15); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        connection = label("", 14);
        button("Paste link", () -> enqueue(clipboardText()));
        settingsButton = button("Connection settings", () -> { settingsOpen = !settingsOpen; refresh(); });
        setup = new LinearLayout(this); setup.setOrientation(LinearLayout.VERTICAL); body.addView(setup);
        LinearLayout root = body; body = setup;
        label("In desktop Papan, open Receive from phone, enable the receiver, then choose Pair a phone.", 14);
        button("Pair desktop", this::scanPair);
        pairLink = field("Pairing link from the desktop QR");
        pairLink.setVisibility(View.GONE);
        manualPairButton = button("Pair from link", () -> pair(pairLink.getText().toString()));
        manualPairButton.setVisibility(View.GONE);
        button("Paste pairing link instead", () -> {
            pairLink.setVisibility(View.VISIBLE); manualPairButton.setVisibility(View.VISIBLE);
            String copied = clipboardText();
            if (copied != null) { pairLink.setText(copied); pair(copied); }
            else { pairLink.requestFocus(); message("Paste or enter the complete pairing link."); }
        });
        address = field("Desktop address · http://192.168.1.10:47778");
        addressButton = button("Update desktop address", () -> {
            try { PhoneStore.get(this).endpoint(Delivery.endpoint(address.getText().toString())); message("Desktop address updated."); sync(); }
            catch (Exception error) { message(error.getMessage()); }
        });
        label("Use a trusted local network. Sharing uses HTTP. Links remain on your phone until the desktop accepts them. Android controls background retry timing; reopen Papan or tap Send pending to retry immediately.", 13);
        body = root;
        button("Send pending / check saves", this::sync);
        label("Pending links", 20); queue = new LinearLayout(this); queue.setOrientation(LinearLayout.VERTICAL); body.addView(queue);
        if (saved != null) { sharedId = saved.getString("sharedId"); pendingPair = saved.getString("pendingPair"); settingsOpen = saved.getBoolean("settingsOpen"); pairLink.setText(saved.getString("pairLink", "")); if (saved.getBoolean("manualPairVisible")) { pairLink.setVisibility(View.VISIBLE); manualPairButton.setVisibility(View.VISIBLE); } }
        refresh();
        if (saved == null) receive(getIntent());
        sync();
    }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); receive(intent); sync(); }
    @Override public void onStart() { super.onStart(); foreground = true; pollUntil = SystemClock.elapsedRealtime() + 30000; sync(); }
    @Override public void onStop() { foreground = false; handler.removeCallbacks(poll); super.onStop(); }
    @Override public void onSaveInstanceState(Bundle state) { state.putString("sharedId", sharedId); state.putString("pendingPair", pendingPair); state.putBoolean("settingsOpen", settingsOpen); state.putBoolean("manualPairVisible", pairLink.getVisibility() == View.VISIBLE); state.putString("pairLink", pairLink.getText().toString()); super.onSaveInstanceState(state); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String clipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData data = clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) return null;
        ClipData.Item item = data.getItemAt(0);
        return item.getText() != null ? item.getText().toString() : item.getUri() != null ? item.getUri().toString() : null;
    }
    private void scanPair() {
        try {
            JSONObject state = PhoneStore.get(this).snapshot();
            if (state.has("config") && state.getJSONArray("entries").length() != 0) throw new Exception("Deliver or dismiss pending links before replacing your pairing. Update the desktop address to reconnect instead.");
            startActivityForResult(new Intent(this, ScanActivity.class), 1);
        } catch (Exception error) { message(error.getMessage()); }
    }
    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 1 && result == RESULT_OK && data != null) pair(data.getStringExtra("pairingLink"));
        else if (request == 1) message("Pairing cancelled. Your existing pairing was kept.");
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setPadding(0, 8, 0, 8); body.addView(view); return view;
    }
    private EditText field(String hint) {
        EditText field = new EditText(this); field.setHint(hint); field.setSingleLine(true); field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); body.addView(field); return field;
    }
    private Button button(String text, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setOnClickListener(view -> action.run()); body.addView(button); return button;
    }
    private void receive(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) enqueue(intent.getStringExtra(Intent.EXTRA_TEXT));
        else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) { pairLink.setText(intent.getData().toString()); pair(intent.getData().toString()); }
        setIntent(new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN));
    }
    private void enqueue(String text) {
        try {
            if (text == null || text.length() > 16384) throw new Exception("Share a web link.");
            Matcher match = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(text);
            if (!match.find()) throw new Exception("No web link was found in the shared text.");
            String url = match.group().replaceAll("[.,;!]+$", ""); URI parsed = new URI(url);
            if (url.length() > 8192 || parsed.getHost() == null || parsed.getUserInfo() != null) throw new Exception("Share a valid HTTP or HTTPS link without embedded credentials.");
            sharedId = PhoneStore.get(this).enqueue(url); pollUntil = SystemClock.elapsedRealtime() + 30000; message("Queued on phone."); refresh(); Delivery.schedule(this); sync();
        } catch (Exception error) { message(error.getMessage()); }
    }
    private void pair(String value) {
        if (value == null) { message("Use the complete pairing link from Papan."); return; }
        pendingPair = value;
        if (!syncing.compareAndSet(false, true)) { message("Waiting for the current delivery before pairing…"); return; }
        pendingPair = null;
        sharedId = null;
        message("Pairing…");
        new Thread(() -> {
            String result;
            try {
                JSONObject before = PhoneStore.get(this).snapshot();
                if (before.has("config") && before.getJSONArray("entries").length() != 0) throw new Exception("Deliver or dismiss pending links before replacing your pairing. Update the desktop address to reconnect instead.");
                PhoneStore.get(this).pair(Delivery.pair(value, (Build.MANUFACTURER + " " + Build.MODEL).substring(0, Math.min(80, (Build.MANUFACTURER + " " + Build.MODEL).length()))));
                result = "Paired with Papan.";
            } catch (Exception error) { result = "Could not pair: " + error.getMessage(); }
            String message = result;
            runOnUiThread(() -> { syncing.set(false); if (isDestroyed()) return; if (message.equals("Paired with Papan.")) { settingsOpen = false; pairLink.setText(""); } message(message); refresh(); Delivery.schedule(this); sync(); });
        }, "papan-pair").start();
    }
    private void sync() {
        if (pendingPair != null) { pair(pendingPair); return; }
        if (!syncing.compareAndSet(false, true)) return;
        refresh();
        new Thread(() -> {
            boolean pending = false;
            try { pending = Delivery.sync(this, new AtomicBoolean(false)); }
            catch (Exception error) { runOnUiThread(() -> message("Your queue was preserved: " + error.getMessage())); }
            if (pending) Delivery.schedule(this);
            runOnUiThread(() -> {
                syncing.set(false); if (isDestroyed()) return; if (pendingPair != null) { pair(pendingPair); return; } refresh();
                if (foreground && SystemClock.elapsedRealtime() < pollUntil) {
                    try {
                        JSONArray entries = PhoneStore.get(this).snapshot().getJSONArray("entries");
                        for (int i = 0; i < entries.length(); i++) if (!entries.getJSONObject(i).optBoolean("received") || entries.getJSONObject(i).getString("status").endsWith("waiting to save")) { handler.removeCallbacks(poll); handler.postDelayed(poll, 1500); break; }
                    } catch (Exception error) { message(error.getMessage()); }
                }
            });
        }, "papan-send").start();
    }
    private void message(String text) { if (status != null) status.setText(text == null ? "Could not complete the operation." : text); }
    private void refresh() {
        try {
            JSONObject state = PhoneStore.get(this).snapshot(); JSONArray entries = state.getJSONArray("entries");
            if (state.has("config")) {
                String endpoint = state.getJSONObject("config").getString("endpoint"); connection.setText("Desktop paired · ready to share");
                if (!address.hasFocus()) address.setText(endpoint);
            } else connection.setText("Pair a desktop to deliver links. You can queue links before pairing.");
            setup.setVisibility(!state.has("config") || settingsOpen ? View.VISIBLE : View.GONE);
            address.setVisibility(state.has("config") ? View.VISIBLE : View.GONE);
            addressButton.setVisibility(state.has("config") ? View.VISIBLE : View.GONE);
            settingsButton.setVisibility(state.has("config") ? View.VISIBLE : View.GONE);
            settingsButton.setText(settingsOpen ? "Close connection settings" : "Connection settings");
            if (sharedId != null) {
                JSONObject current = null;
                for (int i = 0; i < entries.length(); i++) if (entries.getJSONObject(i).getString("id").equals(sharedId)) current = entries.getJSONObject(i);
                message(current == null ? "Saved in Papan." : syncing.get() && state.has("config") ? current.optBoolean("received") ? "Checking save…" : "Sending link…" : current.getString("status"));
            }
            queue.removeAllViews();
            if (entries.length() == 0) {
                TextView empty = new TextView(this); empty.setText(state.has("lastSaved") ? "Saved in Papan. No pending links." : "No pending links."); queue.addView(empty);
            }
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i); String id = entry.getString("id");
                TextView text = new TextView(this); text.setText(entry.getString("url") + "\n" + entry.getString("status")); text.setTextIsSelectable(true); text.setPadding(0, 12, 0, 8); queue.addView(text);
                Button dismiss = new Button(this); dismiss.setText("Dismiss from phone"); dismiss.setAllCaps(false);
                dismiss.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Dismiss this link?").setMessage("This removes the phone's queue entry. If Papan already received it, its desktop entry remains.").setNegativeButton("Keep", null).setPositiveButton("Dismiss", (dialog, which) -> {
                    try { PhoneStore.get(this).remove(id, false); if (id.equals(sharedId)) { sharedId = null; message("Dismissed from phone."); } refresh(); } catch (Exception error) { message(error.getMessage()); }
                }).show()); queue.addView(dismiss);
            }
        } catch (Exception error) { message("Could not read your queue. Files were preserved. " + error.getMessage()); }
    }
}
