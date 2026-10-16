package com.papan.share;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class DesktopConnection {
    private static long lastSearch;
    private static String lastServer = "";
    private static String accessSession = java.util.UUID.randomUUID().toString();
    static synchronized String sessionId() { return accessSession; }
    static synchronized void closeSession(String session) { if (accessSession.equals(session)) accessSession = java.util.UUID.randomUUID().toString(); }
    public static JSONObject request(Context context, String route, JSONObject body) throws Exception {
        return request(context, route, body, sessionId());
    }
    static JSONObject request(Context context, String route, JSONObject body, String session) throws Exception {
        JSONObject state = PhoneStore.get(context).snapshot();
        if (!state.has("config")) throw new Exception("Pair your desktop in Connections first.");
        JSONObject config = state.getJSONObject("config");
        try { return Delivery.request(Delivery.endpoint(config.getString("endpoint")), route, config.getString("token"), body, 10000, session); }
        catch (Exception error) {
            if (!(error.getCause() instanceof IOException) && !(error instanceof Delivery.RequestException && ((Delivery.RequestException)error).status == 401)) throw error;
            if (!recover(context, config)) throw error;
            JSONObject current = PhoneStore.get(context).snapshot().getJSONObject("config");
            return Delivery.request(Delivery.endpoint(current.getString("endpoint")), route, current.getString("token"), body, 10000, session);
        }
    }
    private static synchronized boolean recover(Context context, JSONObject config) throws Exception {
        JSONObject current = PhoneStore.get(context).snapshot().optJSONObject("config");
        if (current == null || !current.optString("token").equals(config.optString("token"))) return false;
        if (!current.optString("endpoint").equals(config.optString("endpoint"))) return true;
        String id = config.optString("serverId");
        if (!id.matches("[a-fA-F0-9-]{36}") || !config.optString("deviceId").matches("[a-fA-F0-9-]{36}") || id.equals(lastServer) && SystemClock.elapsedRealtime() - lastSearch < 15000) return false;
        lastServer = id; lastSearch = SystemClock.elapsedRealtime();
        for (JSONObject candidate : DesktopDiscovery.find(context, id)) try { if (reconnect(context, config, candidate.getString("endpoint"))) return true; } catch (InterruptedException error) { throw error; } catch (Exception ignored) {}
        return false;
    }
    public static boolean reconnect(Context context, JSONObject config, String address) throws Exception {
        String endpoint = Delivery.endpoint(address); byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String nonce = hex(random), server = config.getString("serverId"), device = config.getString("deviceId");
        JSONObject reply = Delivery.request(endpoint, "/v1/identity", "", new JSONObject().put("serverId", server).put("deviceId", device).put("nonce", nonce), 2000);
        if (reply.optInt("version") != 1 || !server.equals(reply.optString("serverId")) || !device.equals(reply.optString("deviceId")) || !endpoint.equals(reply.optString("endpoint")) || !nonce.equals(reply.optString("nonce")) || !reply.optString("proof").matches("[a-f0-9]{64}")) return false;
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(config.getString("token").getBytes(StandardCharsets.UTF_8)), "HmacSHA256"));
        String proof = hex(mac.doFinal(("papan-reconnect-v1\n" + server + "\n" + device + "\n" + endpoint + "\n" + nonce).getBytes(StandardCharsets.UTF_8)));
        return MessageDigest.isEqual(proof.getBytes(StandardCharsets.US_ASCII), reply.getString("proof").getBytes(StandardCharsets.US_ASCII)) && PhoneStore.get(context).reconnect(config, endpoint);
    }
    static String hex(byte[] value) { StringBuilder result = new StringBuilder(); for (byte b : value) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return result.toString(); }
}
