package com.papan.share;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class DesktopDiscovery {
    public static final String TYPE = "_papan-phone._tcp.";
    public static ArrayList<JSONObject> find(Context context, String serverId) throws Exception {
        NsdManager nsd = (NsdManager)context.getSystemService(Context.NSD_SERVICE);
        LinkedBlockingQueue<NsdServiceInfo> found = new LinkedBlockingQueue<>(16);
        NsdManager.DiscoveryListener discovery = new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String type) {}
            public void onDiscoveryStopped(String type) {}
            public void onStartDiscoveryFailed(String type, int error) {}
            public void onStopDiscoveryFailed(String type, int error) {}
            public void onServiceLost(NsdServiceInfo service) {}
            public void onServiceFound(NsdServiceInfo service) {
                String name = service.getServiceName();
                if (name != null && name.startsWith(serverId.isEmpty() ? "Papan " : "Papan " + serverId)) found.offer(service);
            }
        };
        ArrayList<JSONObject> result = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
        long until = SystemClock.elapsedRealtime() + 5000;
        try {
            nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, discovery);
            while (SystemClock.elapsedRealtime() < until && result.size() < 8) {
                NsdServiceInfo service = found.poll(Math.max(1, until - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS);
                if (service == null || !seen.add(service.getServiceName())) continue;
                CountDownLatch resolved = new CountDownLatch(1); NsdServiceInfo[] info = new NsdServiceInfo[1];
                NsdManager.ResolveListener listener = new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo value, int error) { resolved.countDown(); }
                    public void onServiceResolved(NsdServiceInfo value) { info[0] = value; resolved.countDown(); }
                };
                try {
                    nsd.resolveService(service, listener);
                    resolved.await(Math.max(1, Math.min(1500, until - SystemClock.elapsedRealtime())), TimeUnit.MILLISECONDS);
                    if (info[0] == null) continue;
                    String id = attribute(info[0], "id"), endpoint = attribute(info[0], "endpoint"), name = attribute(info[0], "name");
                    if (!id.matches("[a-fA-F0-9-]{36}") || !serverId.isEmpty() && !serverId.equals(id) || !"1".equals(attribute(info[0], "version"))) continue;
                    endpoint = Delivery.endpoint(endpoint);
                    result.add(new JSONObject().put("serverId", id).put("endpoint", endpoint).put("name", name.isEmpty() ? "Papan desktop" : name.substring(0, Math.min(80, name.length()))));
                    if (!serverId.isEmpty()) break;
                } catch (InterruptedException error) { throw error; }
                catch (Exception ignored) {}
                finally { if (Build.VERSION.SDK_INT >= 34) try { nsd.stopServiceResolution(listener); } catch (RuntimeException ignored) {} }
            }
        } finally { try { nsd.stopServiceDiscovery(discovery); } catch (RuntimeException ignored) {} }
        return result;
    }
    private static String attribute(NsdServiceInfo info, String name) {
        byte[] value = info.getAttributes().get(name); return value == null || value.length > 255 ? "" : new String(value, StandardCharsets.UTF_8);
    }
}
