package com.papan.share;

import android.app.Activity;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DesktopPairing {
    private final Activity activity;
    private final Runnable paired;
    private final LinearLayout body;
    private final TextView status;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private AlertDialog dialog;
    private DesktopPairing(Activity activity, Runnable paired) {
        this.activity = activity; this.paired = paired;
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(padding, padding / 3, padding, padding);
        status = new TextView(activity); status.setTextSize(16); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(status);
    }
    public static AlertDialog show(Activity activity, Runnable scanner, Runnable paired) {
        DesktopPairing pairing = new DesktopPairing(activity, paired); ScrollView scroll = new ScrollView(activity); scroll.addView(pairing.body);
        pairing.dialog = new MaterialAlertDialogBuilder(activity).setTitle("Nearby desktops").setView(scroll).setNegativeButton("Cancel", null).setNeutralButton("Scan QR", (d,w) -> scanner.run()).create();
        pairing.dialog.setOnDismissListener(d -> { pairing.cancelled.set(true); pairing.worker.shutdownNow(); }); pairing.dialog.show(); pairing.search(); return pairing.dialog;
    }
    private void ui(Runnable action) { activity.runOnUiThread(() -> { if (!cancelled.get() && !activity.isDestroyed()) action.run(); }); }
    private MaterialButton button(String label, Runnable action) { MaterialButton value = new MaterialButton(activity); value.setText(label); value.setAllCaps(false); value.setOnClickListener(v -> action.run()); return value; }
    private void reset(String message) { body.removeAllViews(); body.addView(status); status.setText(message); }
    private void search() {
        reset("Looking for Papan desktops on this network…");
        worker.execute(() -> { try {
            ArrayList<JSONObject> found = DesktopDiscovery.find(activity.getApplicationContext(), "");
            JSONObject config = PhoneStore.get(activity).snapshot().optJSONObject("config"); String pairedId = config == null ? "" : config.optString("serverId");
            ui(() -> { reset(found.isEmpty() ? "No desktop found. Open Receive from phone on your computer and enable the receiver. Scan QR also works when discovery is blocked." : "Choose a desktop, then approve the matching code on that computer."); for (JSONObject desktop : found) body.addView(button((pairedId.equals(desktop.optString("serverId")) ? "Reconnect · " : "Pair · ") + desktop.optString("name"), () -> select(desktop))); body.addView(button("Search again", this::search)); });
        } catch (InterruptedException ignored) {} catch (Exception error) { ui(() -> { reset(error.getMessage() == null ? "Could not find a desktop." : error.getMessage()); body.addView(button("Search again", this::search)); }); } });
    }
    private void select(JSONObject desktop) {
        reset("Requesting approval on " + desktop.optString("name") + "…");
        worker.execute(() -> {
            String endpoint = desktop.optString("endpoint"), requestId = ""; boolean completed = false;
            try {
                JSONObject state = PhoneStore.get(activity).snapshot(), config = state.optJSONObject("config");
                if (config != null && config.optString("serverId").equals(desktop.optString("serverId"))) {
                    if (!DesktopConnection.reconnect(activity, config, endpoint)) throw new Exception("Could not confirm this desktop's existing pairing.");
                } else {
                    if (config != null && state.getJSONArray("entries").length() > 0) throw new Exception(PhoneStore.PAIRING_PENDING);
                    byte[] random = new byte[16]; new SecureRandom().nextBytes(random); requestId = DesktopConnection.hex(random);
                    String name = (Build.MANUFACTURER + " " + Build.MODEL); name = name.substring(0, Math.min(80, name.length()));
                    JSONObject pending = Delivery.request(endpoint, "/v1/pair-requests", "", new JSONObject().put("id", requestId).put("name", name), 2000);
                    if (!desktop.getString("serverId").equals(pending.getString("serverId")) || !pending.optString("code").matches("[0-9]{6}")) throw new Exception("Invalid desktop pairing response.");
                    String code = pending.getString("code");
                    ui(() -> { reset("Approve this matching code in Papan on " + desktop.optString("name") + "."); TextView digits = new TextView(activity); digits.setText(code); digits.setTextSize(32); digits.setGravity(Gravity.CENTER); digits.setContentDescription("Pairing code " + code); body.addView(digits); });
                    long expires = SystemClock.elapsedRealtime() + 120000;
                    boolean approved = false;
                    while (!cancelled.get() && SystemClock.elapsedRealtime() < expires) {
                        JSONObject reply = Delivery.request(endpoint, "/v1/pair-requests/" + requestId, "", null, 2000);
                        if (!desktop.getString("serverId").equals(reply.optString("serverId")) || !code.equals(reply.optString("code"))) throw new Exception("Desktop pairing changed. Try again.");
                        if ("approved".equals(reply.optString("status"))) {
                            JSONObject pairedConfig = Delivery.pair(reply.getString("pairingLink"), name);
                            if (!desktop.getString("serverId").equals(pairedConfig.getString("serverId"))) throw new Exception("The selected desktop changed.");
                            if (cancelled.get()) return;
                            PhoneStore.get(activity).pair(pairedConfig); approved = true; break;
                        }
                        Thread.sleep(1000);
                    }
                    if (!approved) throw new Exception("Pairing request expired. Try again when the desktop is ready.");
                }
                completed = true; ui(() -> { dialog.dismiss(); paired.run(); });
            } catch (InterruptedException ignored) {} catch (Exception error) { ui(() -> { reset(error.getMessage() == null ? "Could not pair with desktop." : error.getMessage()); body.addView(button("Search again", this::search)); }); }
            finally { if (!completed && !requestId.isEmpty()) try { Delivery.request(endpoint, "/v1/pair-requests/" + requestId + "/cancel", "", new JSONObject(), 2000); } catch (Exception ignored) {} }
        });
    }
}
