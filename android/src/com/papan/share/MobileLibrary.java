package com.papan.share;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
            Set<String> previous = mediaNames(pin);
            for (String key : new String[] { "mediaFile", "items", "kind", "capture", "captureError" }) if (details.has(key)) pin.put(key, details.get(key));
            if (details.has("items")) { JSONObject first = details.getJSONArray("items").getJSONObject(0); pin.put("mediaFile", first.getString("mediaFile")).put("kind", first.getString("kind")); }
            if (details.has("title") && !pin.optBoolean("edited")) pin.put("title", details.getString("title"));
            commit(next);
            deleteUnusedMedia(previous.toArray(new String[0]));
            return true;
        }
        return false;
    }
    public synchronized String addSelection(String collectionId, String url, JSONObject inspection, JSONArray selected) throws Exception {
        if (!LocalCapture.webLink(url).equals(url) || selected.length() == 0 || selected.length() > 50) throw new Exception("Choose between 1 and 50 media items.");
        JSONObject next = snapshot(); requireCollection(next, collectionId); JSONArray pins = next.getJSONArray("pins"), items = new JSONArray(); Set<String> keys = new HashSet<>();
        JSONArray candidates = inspection.getJSONArray("items");
        for (int i = 0; i < selected.length(); i++) {
            String key = selected.getString(i); if (!keys.add(key)) throw new Exception("A media item was selected twice."); JSONObject chosen = null;
            for (int j = 0; j < candidates.length(); j++) if (key.equals(candidates.getJSONObject(j).optString("key"))) chosen = candidates.getJSONObject(j);
            if (chosen == null || !("image".equals(chosen.optString("kind")) || "video".equals(chosen.optString("kind")))) throw new Exception("This media selection is no longer available.");
            items.put(new JSONObject().put("id", UUID.randomUUID().toString()).put("key", key).put("kind", chosen.getString("kind")));
        }
        for (int i = 0; i < pins.length(); i++) {
            JSONObject pin = pins.getJSONObject(i); if (!collectionId.equals(pin.optString("collectionId")) || !url.equals(pin.optString("sourceUrl"))) continue;
            JSONArray existing = pin.optJSONArray("items"); Set<String> savedKeys = new HashSet<>();
            for (int j = 0; existing != null && j < existing.length(); j++) savedKeys.add(existing.getJSONObject(j).optString("key"));
            if (keys.equals(savedKeys) && inspection.optString("engine").equals(pin.optString("engine"))) return pin.getString("id");
            if (existing == null) { pin.put("engine", inspection.getString("engine")).put("items", items).put("capture", "pending").put("captureError", ""); commit(next); return pin.getString("id"); }
        }
        if (pins.length() >= 5000) throw new Exception("Your phone library has reached 5,000 pins.");
        String id = UUID.randomUUID().toString(), title = inspection.optString("title", url);
        pins.put(new JSONObject().put("id", id).put("collectionId", collectionId).put("sourceUrl", url).put("title", title.substring(0, Math.min(200, title.length()))).put("notes", "").put("createdAt", System.currentTimeMillis()).put("engine", inspection.getString("engine")).put("items", items).put("capture", "pending"));
        commit(next); return id;
    }
    public synchronized void edit(String id, String collectionId, String title, String notes) throws Exception {
        title = title.trim(); if (title.isEmpty() || title.length() > 200 || notes.length() > 10000) throw new Exception("Use a title of up to 200 characters and notes of up to 10,000.");
        JSONObject next = snapshot(); requireCollection(next, collectionId); JSONArray pins = next.getJSONArray("pins");
        for (int i = 0; i < pins.length(); i++) if (pins.getJSONObject(i).getString("id").equals(id)) {
            JSONObject pin = pins.getJSONObject(i);
            pin.put("collectionId", collectionId).put("title", title).put("notes", notes).put("edited", true); commit(next); return;
        }
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
        Set<String> names = new HashSet<>(); for (int i = 0; i < removed.length(); i++) names.addAll(mediaNames(removed.getJSONObject(i)));
        deleteUnusedMedia(names.toArray(new String[0]));
    }
    private static Set<String> mediaNames(JSONObject pin) {
        Set<String> names = new HashSet<>(); names.add(pin.optString("mediaFile")); JSONArray items = pin.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item != null) names.add(item.optString("mediaFile")); }
        return names;
    }
    private void deleteUnusedMedia(String... names) {
        Set<String> unused = new HashSet<>(Arrays.asList(names));
        JSONArray pins = state.optJSONArray("pins");
        if (pins == null) return;
        for (int i = 0; i < pins.length(); i++) {
            JSONObject pin = pins.optJSONObject(i);
            if (pin == null) return;
            unused.removeAll(mediaNames(pin));
        }
        for (String name : unused) { File saved = mediaFile(name); if (saved != null) saved.delete(); }
    }
    public File mediaFile(String name) {
        return name.matches("[a-fA-F0-9-]{36}\\.(jpg|png|webp|gif|mp4|webm)") ? new File(media, name) : null;
    }
}
