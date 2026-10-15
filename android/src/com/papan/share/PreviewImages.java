package com.papan.share;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PreviewImages {
    interface Load { Bitmap run() throws Exception; }
    interface Result { void accept(Bitmap bitmap); }
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>((int)Math.min(32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private final Map<String, ArrayList<Result>> pending = new HashMap<>();
    private final LruCache<String, Double> ratios = new LruCache<>(4096);
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    synchronized Bitmap peek(String key) { return cache.get(key); }
    void load(String key, Load load, Result result) {
        final ArrayList<Result> waiting;
        synchronized (this) {
            Bitmap bitmap = cache.get(key); if (bitmap != null) { result.accept(bitmap); return; }
            if (pending.containsKey(key)) { pending.get(key).add(result); return; }
            waiting = new ArrayList<>(); waiting.add(result); pending.put(key, waiting);
        }
        workers.execute(() -> {
            Bitmap decoded = null; try { decoded = load.run(); } catch (Exception ignored) {}
            final Bitmap bitmap = decoded;
            main.post(() -> {
                synchronized (PreviewImages.this) {
                    if (pending.get(key) != waiting) return;
                    pending.remove(key); if (bitmap != null) cache.put(key, bitmap);
                }
                for (Result callback : waiting) callback.accept(bitmap);
            });
        });
    }
    synchronized void retainRemote(Set<String> authorized) {
        Set<String> keys = new HashSet<>(cache.snapshot().keySet()); keys.addAll(pending.keySet()); keys.addAll(ratios.snapshot().keySet());
        for (String key : keys) if (key.startsWith("remote:") && !authorized.contains(key.substring(7, key.indexOf(':', 7)))) { cache.remove(key); pending.remove(key); ratios.remove(key); }
    }
    synchronized boolean measure(String collection, String item, double ratio) { String key = "remote:" + collection + ":" + item; Double previous = ratios.put(key, ratio); return previous == null || Math.abs(previous - ratio) > .001; }
    synchronized Map<String, Double> ratios(String collection) { Map<String, Double> result = new HashMap<>(); String prefix = "remote:" + collection + ":"; for (Map.Entry<String, Double> entry : ratios.snapshot().entrySet()) if (entry.getKey().startsWith(prefix)) result.put(entry.getKey().substring(prefix.length()), entry.getValue()); return result; }
    synchronized void close() { cache.evictAll(); ratios.evictAll(); pending.clear(); workers.shutdownNow(); }
}
