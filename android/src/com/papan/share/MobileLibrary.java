package com.papan.share;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MobileLibrary {
    private static MobileLibrary instance;
    private final AtomicFile file;
    private final File media;
    private JSONObject state;
    public static synchronized MobileLibrary get(Context context) throws Exception {
        if (instance == null) instance = new MobileLibrary(context.getApplicationContext());
        return instance;
    }
    private MobileLibrary(Context context) throws Exception {
        file = new AtomicFile(new File(context.getFilesDir(), "mobile-library.json"));
        media = new File(context.getFilesDir(), "mobile-media");
        if (!media.isDirectory() && !media.mkdirs()) throw new Exception("Could not open local media storage.");
        if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()) {
            state = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
            if (state.getInt("version") != 1 || state.getJSONArray("collections").length() > 1000 || state.getJSONArray("pins").length() > 5000) throw new Exception("Mobile library could not be read. Your files were preserved.");
        } else state = new JSONObject().put("version", 1).put("collections", new JSONArray()).put("pins", new JSONArray());
    }
    public synchronized JSONObject snapshot() throws Exception { return new JSONObject(state.toString()); }
    private void commit(JSONObject next) throws Exception {
        FileOutputStream stream = null;
        try { stream = file.startWrite(); stream.write(next.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(stream); state = next; }
        catch (Exception error) { file.failWrite(stream); throw error; }
    }
    public synchronized String create(String name) throws Exception {
        name = name.trim(); if (name.isEmpty() || name.length() > 80) throw new Exception("Use a collection name between 1 and 80 characters.");
        JSONObject next = snapshot(); JSONArray collections = next.getJSONArray("collections");
        if (collections.length() >= 1000) throw new Exception("Your phone library has reached 1,000 collections.");
        String id = UUID.randomUUID().toString();
        collections.put(new JSONObject().put("id", id).put("name", name).put("createdAt", System.currentTimeMillis())); commit(next); return id;
    }
    public synchronized void rename(String id, String name) throws Exception {
        name = name.trim(); if (name.isEmpty() || name.length() > 80) throw new Exception("Use a collection name between 1 and 80 characters.");
        JSONObject next = snapshot(); requireCollection(next, id).put("name", name); commit(next);
    }
    private JSONObject requireCollection(JSONObject data, String id) throws Exception {
        JSONArray collections = data.getJSONArray("collections");
        for (int i = 0; i < collections.length(); i++) if (collections.getJSONObject(i).getString("id").equals(id)) return collections.getJSONObject(i);
        throw new Exception("This phone collection no longer exists.");
    }
    public synchronized String add(String collectionId, String url) throws Exception {
        if (!LocalCapture.webLink(url).equals(url)) throw new Exception("Use a complete web link.");
        JSONObject next = snapshot(); requireCollection(next, collectionId); JSONArray pins = next.getJSONArray("pins");
        for (int i = 0; i < pins.length(); i++) { JSONObject pin = pins.getJSONObject(i); if (pin.getString("collectionId").equals(collectionId) && pin.getString("sourceUrl").equals(url)) return pin.getString("id"); }
        if (pins.length() >= 5000) throw new Exception("Your phone library has reached 5,000 pins.");
        String id = UUID.randomUUID().toString();
        pins.put(new JSONObject().put("id", id).put("collectionId", collectionId).put("sourceUrl", url).put("title", url.substring(0, Math.min(200, url.length()))).put("notes", "").put("createdAt", System.currentTimeMillis()).put("capture", "pending"));
        commit(next); return id;
    }
    public synchronized boolean captured(String id, JSONObject details) throws Exception {
        JSONObject next = snapshot(); JSONArray pins = next.getJSONArray("pins");
        for (int i = 0; i < pins.length(); i++) if (pins.getJSONObject(i).getString("id").equals(id)) {
            JSONObject pin = pins.getJSONObject(i);
            for (String key : new String[] { "mediaFile", "kind", "capture", "captureError" }) if (details.has(key)) pin.put(key, details.get(key));
            if (details.has("title") && !pin.optBoolean("edited")) pin.put("title", details.getString("title"));
            commit(next); return true;
        }
        return false;
    }
    public synchronized void edit(String id, String title, String notes) throws Exception {
        title = title.trim(); if (title.isEmpty() || title.length() > 200 || notes.length() > 10000) throw new Exception("Use a title of up to 200 characters and notes of up to 10,000.");
        JSONObject next = snapshot(); JSONArray pins = next.getJSONArray("pins");
        for (int i = 0; i < pins.length(); i++) if (pins.getJSONObject(i).getString("id").equals(id)) { pins.getJSONObject(i).put("title", title).put("notes", notes).put("edited", true); commit(next); return; }
        throw new Exception("This pin no longer exists.");
    }
    public synchronized void retry(String id) throws Exception { captured(id, new JSONObject().put("capture", "pending").put("captureError", "")); }
    public synchronized void removePin(String id) throws Exception { remove(id, false); }
    public synchronized void removeCollection(String id) throws Exception { remove(id, true); }
    private void remove(String id, boolean collection) throws Exception {
        JSONObject next = snapshot(); JSONArray pins = next.getJSONArray("pins"), remaining = new JSONArray(), removed = new JSONArray();
        for (int i = 0; i < pins.length(); i++) { JSONObject pin = pins.getJSONObject(i); if (pin.getString(collection ? "collectionId" : "id").equals(id)) removed.put(pin); else remaining.put(pin); }
        next.put("pins", remaining);
        if (collection) { JSONArray collections = next.getJSONArray("collections"), kept = new JSONArray(); for (int i = 0; i < collections.length(); i++) if (!collections.getJSONObject(i).getString("id").equals(id)) kept.put(collections.getJSONObject(i)); next.put("collections", kept); }
        commit(next);
        for (int i = 0; i < removed.length(); i++) { File saved = mediaFile(removed.getJSONObject(i).optString("mediaFile")); if (saved != null) saved.delete(); }
    }
    public File mediaFile(String name) {
        return name.matches("[a-fA-F0-9-]{36}\\.(jpg|png|webp|gif|mp4|webm)") ? new File(media, name) : null;
    }
}
