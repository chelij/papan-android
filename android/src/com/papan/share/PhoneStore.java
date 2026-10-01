package com.papan.share;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// Only app-private storage is used; Android backups are disabled in the manifest.
public final class PhoneStore {
    private static PhoneStore instance;
    private final AtomicFile file;
    private JSONObject state;
    public static synchronized PhoneStore get(Context context) throws Exception {
        if (instance == null) instance = new PhoneStore(context.getApplicationContext());
        return instance;
    }
    private PhoneStore(Context context) throws Exception {
        file = new AtomicFile(new File(context.getFilesDir(), "phone-inbox.json"));
        if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()) {
            state = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
            if (state.getInt("version") != 1 || state.getJSONArray("entries").length() > 100) throw new Exception("Phone inbox could not be read. Your files were preserved.");
        } else state = new JSONObject().put("version", 1).put("entries", new JSONArray());
    }
    public synchronized JSONObject snapshot() throws Exception { return new JSONObject(state.toString()); }
    private void commit(JSONObject next) throws Exception {
        FileOutputStream stream = null;
        try { stream = file.startWrite(); stream.write(next.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(stream); state = next; }
        catch (Exception error) { file.failWrite(stream); throw error; }
    }
    public synchronized String enqueue(String url) throws Exception {
        JSONObject next = snapshot(); JSONArray entries = next.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) if (entries.getJSONObject(i).getString("url").equals(url)) return entries.getJSONObject(i).getString("id");
        if (entries.length() >= 100) throw new Exception("Phone queue is full. Deliver or dismiss existing links first.");
        String id = UUID.randomUUID().toString();
        entries.put(new JSONObject().put("id", id).put("url", url).put("received", false).put("status", "queued on phone"));
        commit(next); return id;
    }
    public synchronized void pair(JSONObject config) throws Exception {
        JSONObject next = snapshot();
        if (next.has("config") && next.getJSONArray("entries").length() != 0) throw new Exception("Deliver or dismiss pending links before replacing your pairing. To reconnect to the same desktop, update its address instead.");
        next.put("config", config); commit(next);
    }
    public synchronized void endpoint(String endpoint) throws Exception {
        JSONObject next = snapshot(); if (!next.has("config")) throw new Exception("Pair a desktop first.");
        next.getJSONObject("config").put("endpoint", endpoint); commit(next);
    }
    public synchronized void update(String id, boolean received, String status) throws Exception {
        JSONObject next = snapshot(); JSONArray entries = next.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if (entry.getString("id").equals(id)) { entry.put("received", received || entry.optBoolean("received")).put("status", status); commit(next); return; }
        }
    }
    public synchronized void remove(String id, boolean saved) throws Exception {
        JSONObject next = snapshot(); JSONArray entries = next.getJSONArray("entries"), remaining = new JSONArray();
        for (int i = 0; i < entries.length(); i++) if (!entries.getJSONObject(i).getString("id").equals(id)) remaining.put(entries.getJSONObject(i));
        next.put("entries", remaining);
        if (saved) next.put("lastSaved", System.currentTimeMillis());
        commit(next);
    }
}
