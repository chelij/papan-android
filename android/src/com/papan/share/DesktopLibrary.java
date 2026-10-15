package com.papan.share;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

public final class DesktopLibrary {
    private final Context context;
    public DesktopLibrary(Context context) { this.context = context.getApplicationContext(); }
    private JSONObject config() throws Exception {
        JSONObject state = PhoneStore.get(context).snapshot();
        if (!state.has("config")) throw new Exception("Pair your desktop in Connections first.");
        return state.getJSONObject("config");
    }
    public JSONObject request(String route, JSONObject body) throws Exception {
        return DesktopConnection.request(context, route, body);
    }
    public boolean supported() throws Exception {
        try { return request("/v1/capabilities", null).optBoolean("selectedDestination"); }
        catch (Delivery.RequestException error) { if (error.status == 404) return false; throw error; }
    }
    public JSONArray collections() throws Exception {
        JSONArray values = request("/v1/collections", null).getJSONArray("collections");
        if (values.length() > 1000) throw new Exception("Desktop has too many open collections.");
        for (int i = 0; i < values.length(); i++) { JSONObject value = values.getJSONObject(i); if (!value.getString("id").matches("[a-fA-F0-9-]{36}") || value.getString("name").length() > 80) throw new Exception("Invalid collection response."); }
        return values;
    }
    public JSONObject pins(String id, int offset) throws Exception { return pins(id, offset, ""); }
    public JSONObject pins(String id, int offset, String query) throws Exception {
        JSONObject page = request("/v1/collections/" + id + "/pins?offset=" + offset + "&limit=40&q=" + android.net.Uri.encode(query), null);
        JSONArray values = page.getJSONArray("pins"); int next = page.isNull("nextOffset") ? -1 : page.getInt("nextOffset");
        if (!id.equals(page.getString("collectionId")) || values.length() > 40 || next != -1 && (values.length() == 0 || next != offset + values.length())) throw new Exception("Invalid desktop page.");
        for (int i = 0; i < values.length(); i++) { JSONObject pin = values.getJSONObject(i); if (!id.equals(pin.getString("collectionId")) || !pin.getString("id").matches("[a-fA-F0-9-]{36}")) throw new Exception("Invalid desktop pin."); }
        return page;
    }
    public JSONObject loadedPins(String id, int count, String query) throws Exception {
        JSONArray pins = new JSONArray(); int offset = 0;
        do {
            JSONObject page = pins(id, offset, query); JSONArray values = page.getJSONArray("pins");
            int next = page.isNull("nextOffset") ? -1 : page.getInt("nextOffset");
            for (int i = 0; i < values.length(); i++) pins.put(values.getJSONObject(i));
            offset = next;
        } while (offset >= 0 && pins.length() < count);
        return new JSONObject().put("collectionId", id).put("pins", pins).put("nextOffset", offset < 0 ? JSONObject.NULL : offset);
    }
    public void unlock(String id, String password) throws Exception { request("/v1/collections/" + id + "/unlock", new JSONObject().put("password", password)); }
    public void clear() { try { request("/v1/access/clear", new JSONObject()); } catch (Exception ignored) {} }
    public byte[] media(String value) throws Exception {
        JSONObject config = config(); String endpoint = Delivery.endpoint(config.getString("endpoint")); URI uri = new URI(value), base = new URI(endpoint);
        if (!base.getScheme().equals(uri.getScheme()) || !base.getAuthority().equals(uri.getAuthority()) || uri.getFragment() != null || !uri.getPath().matches("/v1/collections/[a-fA-F0-9-]{36}/pins/[a-fA-F0-9-]{36}/items/[a-fA-F0-9-]{36}/media") || !("variant=preview".equals(uri.getQuery()) || "variant=thumbnail".equals(uri.getQuery()))) throw new Exception("Invalid desktop media address.");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(10000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Authorization", "Bearer " + config.getString("token"));
        try {
            if (connection.getResponseCode() != 200) throw new Exception("Preview is unavailable.");
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192]; int size;
                while ((size = stream.read(chunk)) != -1) { if (bytes.size() + size > 8 * 1024 * 1024) throw new Exception("Preview is too large."); bytes.write(chunk, 0, size); }
                return bytes.toByteArray();
            }
        } finally { connection.disconnect(); }
    }
    public java.util.Map<String, String> videoHeaders(String value) throws Exception {
        JSONObject config = config(); URI uri = new URI(value), base = new URI(Delivery.endpoint(config.getString("endpoint")));
        if (!base.getScheme().equals(uri.getScheme()) || !base.getAuthority().equals(uri.getAuthority()) || uri.getFragment() != null || !uri.getPath().matches("/v1/collections/[a-fA-F0-9-]{36}/pins/[a-fA-F0-9-]{36}/items/[a-fA-F0-9-]{36}/media") || !("variant=preview".equals(uri.getQuery()) || "variant=original".equals(uri.getQuery()))) throw new Exception("Invalid desktop video address.");
        return java.util.Collections.singletonMap("Authorization", "Bearer " + config.getString("token"));
    }
}
