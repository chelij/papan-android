package com.papan.share;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Delivery {
    private static final AtomicBoolean syncing = new AtomicBoolean(false);
    public static String endpoint(String value) throws Exception {
        URI uri = new URI(value.trim()); String host = uri.getHost();
        if (!"http".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")) || uri.getPort() < 1024 || uri.getPort() > 65535) throw new Exception("Use your desktop's local address, such as http://192.168.1.10:47778.");
        String[] parts = host.split("\\."); boolean local = false;
        if (parts.length == 4) {
            int[] n = new int[4];
            for (int i = 0; i < 4; i++) { if (!parts[i].matches("[0-9]{1,3}")) throw new Exception("A local IPv4 address is required."); n[i] = Integer.parseInt(parts[i]); if (n[i] > 255) throw new Exception("Invalid network address."); }
            local = n[0] == 10 || n[0] == 192 && n[1] == 168 || n[0] == 172 && n[1] >= 16 && n[1] <= 31 || host.equals("127.0.0.1");
        }
        if (!local) throw new Exception("Pair with a local network address.");
        return "http://" + host + ":" + uri.getPort();
    }
    public static JSONObject pair(String value, String name) throws Exception {
        Uri uri = Uri.parse(value.trim()); String address, code;
        if ("papan-pair".equals(uri.getScheme()) && "connect".equals(uri.getHost())) { address = uri.getQueryParameter("endpoint"); code = uri.getQueryParameter("code"); }
        else if ("http".equals(uri.getScheme()) && "/pair".equals(uri.getPath())) { address = "http://" + uri.getAuthority(); code = uri.getFragment(); }
        else throw new Exception("Paste the complete pairing link from Papan.");
        address = endpoint(address == null ? "" : address);
        if (code == null || !code.matches("[a-f0-9]{32}")) throw new Exception("Pairing link is missing its code.");
        JSONObject config = request(address, "/v1/pair", "", new JSONObject().put("code", code).put("name", name));
        if (config.getInt("version") != 1 || !config.getString("endpoint").equals(address) || !config.getString("token").matches("[A-Za-z0-9_-]{43}") || !config.getString("serverId").matches("[a-f0-9-]{36}")) throw new Exception("Invalid pairing response.");
        return config;
    }
    public static JSONObject request(String endpoint, String route, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new java.net.URL(endpoint + route).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(10000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8); connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) throw new Exception("Desktop returned HTTP " + status + ".");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = input) {
                byte[] buffer = new byte[4096]; int length;
                while ((length = stream.read(buffer)) != -1) { if (bytes.size() + length > 32768) throw new Exception("Desktop response is too large."); bytes.write(buffer, 0, length); }
            }
            JSONObject result = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (status < 200 || status >= 300) throw new Exception(result.optString("error", "Desktop returned HTTP " + status + "."));
            return result;
        } catch (java.io.IOException error) { throw new Exception("Desktop unavailable. Check Papan, Wi-Fi, and the desktop firewall.", error); }
        finally { connection.disconnect(); }
    }
    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(new JobInfo.Builder(1, new ComponentName(context, DeliveryJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setMinimumLatency(15000).setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public static boolean sync(Context context, AtomicBoolean cancelled) throws Exception {
        if (!syncing.compareAndSet(false, true)) return true;
        try {
            PhoneStore store = PhoneStore.get(context); JSONObject state = store.snapshot();
            if (!state.has("config")) return false;
            JSONObject config = state.getJSONObject("config"); JSONArray entries = state.getJSONArray("entries");
            String endpoint = endpoint(config.getString("endpoint")), token = config.getString("token");
            for (int i = 0; i < entries.length() && !cancelled.get(); i++) {
                JSONObject entry = entries.getJSONObject(i); String id = entry.getString("id"); boolean received = entry.optBoolean("received");
                try {
                    JSONObject receipt = request(endpoint, received ? "/v1/shares/" + id : "/v1/shares", token,
                        received ? null : new JSONObject().put("id", id).put("url", entry.getString("url")));
                    if (!receipt.getString("id").equals(id) || !receipt.optBoolean("received")) throw new Exception("Invalid share receipt.");
                    if ("saved".equals(receipt.getString("state"))) store.remove(id, true);
                    else store.update(id, true, "failed".equals(receipt.getString("state")) ? "received by Papan · " + receipt.optString("error") : "received by Papan · waiting to save");
                } catch (Exception error) {
                    store.update(id, received, (received ? "received by Papan · " : "queued on phone · ") + (error.getMessage() == null ? "desktop unavailable" : error.getMessage()));
                    break;
                }
            }
            return store.snapshot().getJSONArray("entries").length() > 0;
        } finally { syncing.set(false); }
    }
}
