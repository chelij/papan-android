package com.papan.share;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.Field;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public final class MobileLibraryStorageTest extends Instrumentation {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static JSONObject pin(MobileLibrary store, String id) throws Exception {
        JSONArray pins = store.snapshot().getJSONArray("pins");
        for (int i = 0; i < pins.length(); i++) if (id.equals(pins.getJSONObject(i).getString("id"))) return pins.getJSONObject(i);
        throw new AssertionError("Saved pin missing.");
    }
    private static byte[] image(int color) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle(); return bytes.toByteArray();
    }
    private static void selectedStorage(MobileLibrary store, Context context, File database) throws Exception {
        String collection = store.create("Attachment storage"), url = "https://example.com/selected", legacy = store.add(collection, url);
        File old = store.mediaFile(legacy + ".jpg"); Files.write(old.toPath(), image(android.graphics.Color.RED)); store.captured(legacy, new JSONObject().put("mediaFile", old.getName()).put("kind", "image").put("capture", "saved"));
        JSONObject plan = new JSONObject().put("engine", "direct").put("items", new JSONArray().put(new JSONObject().put("key", "one").put("kind", "image")).put(new JSONObject().put("key", "two").put("kind", "image")));
        JSONArray keys = new JSONArray().put("one").put("two");
        require(store.addSelection(collection, url, plan, keys).equals(legacy), "Choosing media duplicated an existing legacy pin.");
        require(store.addSelection(collection, url, plan, keys).equals(legacy) && old.isFile(), "Repeated media selection duplicated a pin or deleted its previous preview.");
        JSONArray items = pin(store, legacy).getJSONArray("items");
        for (int i = 0; i < items.length(); i++) { String name = items.getJSONObject(i).getString("id") + ".png"; Files.write(store.mediaFile(name).toPath(), image(android.graphics.Color.BLUE)); items.getJSONObject(i).put("mediaFile", name); }
        byte[] previous = Files.readAllBytes(database.toPath()); File blocked = new File(database + ".new"), marker = new File(blocked, "fixture"); require(blocked.mkdir() && marker.createNewFile(), "Could not block attachment commit.");
        boolean failed = false; try { store.captured(legacy, new JSONObject().put("items", items).put("capture", "saved")); } catch (Exception expected) { failed = true; } finally { marker.delete(); blocked.delete(); }
        require(failed && old.isFile() && Arrays.equals(previous, Files.readAllBytes(database.toPath())), "Failed attachment commit changed the old preview or library.");
        store.captured(legacy, new JSONObject().put("items", items).put("capture", "saved"));
        require(!old.exists() && pin(store, legacy).getString("mediaFile").equals(items.getJSONObject(0).getString("mediaFile")), "Attachments did not retain their v1 cover or clean the old media.");
        String second = store.add(collection, "https://example.com/shared-selection"); store.captured(second, new JSONObject().put("items", items).put("capture", "saved")); store.removePin(legacy);
        for (int i = 0; i < items.length(); i++) require(store.mediaFile(items.getJSONObject(i).getString("mediaFile")).isFile(), "Removing a pin deleted another pin's attachment.");
        store.removeCollection(collection);
        for (int i = 0; i < items.length(); i++) require(!store.mediaFile(items.getJSONObject(i).getString("mediaFile")).exists(), "Attachment cleanup left an unreferenced file.");
    }
    private static void moveStorage(MobileLibrary store, Context context, File database) throws Exception {
        String source = store.create("Move source"), target = store.create("Move target"), id = store.add(source, "https://example.com/move");
        File media = store.mediaFile(id + ".png"); byte[] bytes = image(android.graphics.Color.GREEN); Files.write(media.toPath(), bytes);
        store.captured(id, new JSONObject().put("kind", "image").put("mediaFile", media.getName()).put("capture", "saved"));
        JSONObject original = pin(store, id); String duplicate = store.add(target, original.getString("sourceUrl"));
        byte[] previous = Files.readAllBytes(database.toPath()); boolean rejected = false;
        try { store.edit(id, "missing", "Moved title", "Moved notes"); } catch (Exception expected) { rejected = true; }
        require(rejected && original.toString().equals(pin(store, id).toString()) && Arrays.equals(previous, Files.readAllBytes(database.toPath())), "Invalid move changed a pin or its library.");
        File blocked = new File(database + ".new"), marker = new File(blocked, "fixture"); require(blocked.mkdir() && marker.createNewFile(), "Could not block move commit.");
        boolean failed = false;
        try { store.edit(id, target, "Moved title", "Moved notes"); } catch (Exception expected) { failed = true; } finally { marker.delete(); blocked.delete(); }
        require(failed && original.toString().equals(pin(store, id).toString()) && Arrays.equals(previous, Files.readAllBytes(database.toPath())) && Arrays.equals(bytes, Files.readAllBytes(media.toPath())), "Failed move lost pin state or media.");
        store.edit(id, target, "Moved title", "Moved notes");
        java.lang.reflect.Constructor<MobileLibrary> constructor = MobileLibrary.class.getDeclaredConstructor(Context.class); constructor.setAccessible(true);
        JSONObject moved = pin(constructor.newInstance(context), id);
        require(store.snapshot().getJSONArray("pins").length() == 2 && duplicate.equals(pin(store, duplicate).getString("id")), "Moving merged or removed an existing destination pin.");
        require(target.equals(moved.getString("collectionId")) && "Moved title".equals(moved.getString("title")) && "Moved notes".equals(moved.getString("notes")), "Reopening lost the edited destination/title/notes.");
        for (String key : new String[] {"id", "sourceUrl", "createdAt", "mediaFile", "kind", "capture"}) require(original.get(key).equals(moved.get(key)), "Moving changed " + key);
        store.removeCollection(source); require(media.isFile() && Arrays.equals(bytes, Files.readAllBytes(media.toPath())), "Removing the old collection deleted moved media.");
        store.removeCollection(target); require(!media.exists(), "Removing the destination retained unused moved media.");
    }
    private static void captureRollback(MobileLibrary store, Context context, File database) throws Exception {
        byte[] original = image(android.graphics.Color.RED), replacement = image(android.graphics.Color.BLUE);
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread responder = new Thread(() -> {
                while (!server.isClosed()) try (Socket socket = server.accept()) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String line; while ((line = input.readLine()) != null && !line.isEmpty()) {}
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: " + replacement.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(replacement);
                } catch (java.io.IOException error) { if (server.isClosed()) return; }
            }); responder.setDaemon(true); responder.start();
            String collection = store.create("Capture rollback"), id = store.add(collection, "http://127.0.0.1:" + server.getLocalPort() + "/capture.png");
            File cached = store.mediaFile(id + ".png"); Files.write(cached.toPath(), original);
            store.captured(id, new JSONObject().put("mediaFile", cached.getName()).put("kind", "image").put("capture", "saved")); store.retry(id);
            byte[] previous = Files.readAllBytes(database.toPath());
            File blocked = new File(database + ".new"), marker = new File(blocked, "fixture");
            require(blocked.mkdir() && marker.createNewFile(), "Could not block the capture commit.");
            boolean failed = false;
            try { LocalCapture.sync(context); } catch (Exception expected) { failed = true; }
            finally { marker.delete(); blocked.delete(); }
            require(failed && Arrays.equals(previous, Files.readAllBytes(database.toPath())), "Failed capture commit changed the library.");
            require(Arrays.equals(original, Files.readAllBytes(cached.toPath())), "Failed same-extension capture overwrote the existing preview.");
            require(new File(context.getFilesDir(), "mobile-media").list().length == 1, "Failed capture left an unused media file.");
            LocalCapture.sync(context);
            File captured = store.mediaFile(pin(store, id).getString("mediaFile"));
            require(!cached.exists() && Arrays.equals(replacement, Files.readAllBytes(captured.toPath())), "Successful recapture did not replace and clean up the old preview.");
            store.removeCollection(collection);
        }
    }
    private static void captureVideos(MobileLibrary store, Context context) throws Exception {
        byte[] video = new byte[] {0, 0, 0, 8, 'f', 't', 'y', 'p'}, poster = image(android.graphics.Color.GREEN);
        java.util.concurrent.atomic.AtomicBoolean available = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (ServerSocket server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            String base = "http://127.0.0.1:" + server.getLocalPort();
            Thread responder = new Thread(() -> {
                while (!server.isClosed()) try (Socket socket = server.accept()) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String path = input.readLine().split(" ")[1], line, referer = "";
                    while ((line = input.readLine()) != null && !line.isEmpty()) if (line.toLowerCase(java.util.Locale.ROOT).startsWith("referer:")) referer = line.substring(8).trim();
                    String type = "text/html", extra = ""; int status = 200; byte[] body;
                    if (path.equals("/redirect")) { status = 302; extra = "Location: /pages/base\r\n"; body = new byte[0]; }
                    else if (path.startsWith("/media/video")) { type = path.contains("webm") ? "video/webm" : "video/mp4"; body = video; if (!available.get() || path.contains("signed") && (!path.endsWith("?a=1&b=2") || referer.isEmpty())) status = 403; }
                    else if (path.equals("/poster")) { type = "image/png"; body = poster; }
                    else if (path.equals("/large")) { type = "video/mp4"; extra = "Content-Length: 33554433\r\n"; body = new byte[0]; }
                    else {
                        String html = "<title>Phone video test</title><meta property='og:image' content='/poster'>";
                        if (path.equals("/og")) html += "<meta content='/media/video.mp4' property='og:video'>";
                        else if (path.equals("/twitter")) html += "<meta name=twitter:player:stream content=/media/video.webm>";
                        else if (path.equals("/video")) html += "<video controls src='/media/video.mp4'></video>";
                        else if (path.equals("/sources")) html += "<meta property=og:video content='/player'><video><source src='/player'><source type=video/mp4 src=/media/video.mp4></video>";
                        else if (path.equals("/pages/base")) html += "<base href='../media/'><video><source src='video.mp4'></video>";
                        else if (path.equals("/signed")) html += "<meta property='og:video:url' content='/media/video-signed.mp4?a=1&amp;b=2'>";
                        else if (path.equals("/invalid")) html += "<meta property=og:video content='file:///private.mp4'>";
                        else if (path.equals("/oversized")) html += "<video src='/large'></video>";
                        body = html.getBytes(StandardCharsets.UTF_8);
                    }
                    socket.getOutputStream().write(("HTTP/1.1 " + status + " Response\r\nContent-Type: " + type + "\r\n" + extra + (extra.contains("Content-Length:") ? "" : "Content-Length: " + body.length + "\r\n") + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().write(body);
                } catch (Exception error) { if (server.isClosed()) return; }
            }); responder.setDaemon(true); responder.start();
            String collection = store.create("Video capture check");
            for (String route : new String[] {"/og", "/twitter", "/video", "/sources", "/redirect", "/signed", "/media/video.mp4"}) {
                String id = store.add(collection, base + route); LocalCapture.sync(context); JSONObject captured = pin(store, id);
                require("saved".equals(captured.optString("capture")) && "video".equals(captured.optString("kind")), "Page saved a poster instead of video: " + route + " " + captured);
                File file = store.mediaFile(captured.getString("mediaFile")); require(Arrays.equals(video, Files.readAllBytes(file.toPath())), "Saved video bytes changed: " + route);
                if (route.equals("/og")) {
                    available.set(false); store.retry(id); LocalCapture.sync(context);
                    require("failed".equals(pin(store, id).optString("capture")) && file.getName().equals(pin(store, id).optString("mediaFile")) && Arrays.equals(video, Files.readAllBytes(file.toPath())), "Failed video retry replaced its offline copy with a poster."); available.set(true);
                    store.retry(id); LocalCapture.sync(context); require(!file.exists(), "Successful video recapture retained its old file.");
                }
            }
            for (String route : new String[] {"/invalid", "/oversized"}) {
                String id = store.add(collection, base + route); LocalCapture.sync(context); JSONObject captured = pin(store, id);
                require("failed".equals(captured.optString("capture")) && "image".equals(captured.optString("kind")) && Arrays.equals(poster, Files.readAllBytes(store.mediaFile(captured.getString("mediaFile")).toPath())), "Failed video did not retain its link and poster: " + route);
            }
            store.removeCollection(collection);
        }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            require("com.papan.share.test".equals(context.getPackageName()), "Use the isolated test package.");
            require("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE), "Use a disposable emulator, never a phone.");
            File database = new File(context.getFilesDir(), "mobile-library.json");
            require(!database.exists(), "Clear only this isolated emulator test package before running.");
            MobileLibrary store = MobileLibrary.get(context);
            String collection = store.create("Storage check"), id = store.add(collection, "https://example.com/first");
            File old = store.mediaFile(id + ".jpg"), current = store.mediaFile(id + ".png");
            Files.write(old.toPath(), new byte[] {1, 2, 3});
            store.captured(id, new JSONObject().put("mediaFile", old.getName()).put("kind", "image").put("capture", "saved"));
            Files.write(current.toPath(), new byte[] {4, 5, 6});
            store.captured(id, new JSONObject().put("mediaFile", current.getName()).put("kind", "image").put("capture", "saved"));
            require(!old.exists() && current.isFile(), "Replacing a preview retained its unused old file.");

            store.captured(id, new JSONObject().put("mediaFile", current.getName()).put("capture", "saved"));
            store.captured(id, new JSONObject().put("capture", "failed").put("captureError", "Fixture failure"));
            require(Arrays.equals(Files.readAllBytes(current.toPath()), new byte[] {4, 5, 6}), "Same-file or failed capture removed the cached preview.");

            File replacement = store.mediaFile(id + ".webp");
            Files.write(replacement.toPath(), new byte[] {7, 8, 9});
            byte[] previous = Files.readAllBytes(database.toPath());
            File blocked = new File(database + ".new"), marker = new File(blocked, "fixture");
            require(blocked.mkdir() && marker.createNewFile(), "Could not set up the failed-write fixture.");
            boolean failed = false;
            try { store.captured(id, new JSONObject().put("mediaFile", replacement.getName()).put("capture", "saved")); }
            catch (Exception expected) { failed = true; }
            finally { marker.delete(); blocked.delete(); }
            require(failed && current.isFile() && current.getName().equals(pin(store, id).getString("mediaFile")), "Failed commit changed or deleted the prior preview.");
            require(Arrays.equals(previous, Files.readAllBytes(database.toPath())), "Failed commit changed the saved library bytes.");

            Field singleton = MobileLibrary.class.getDeclaredField("instance"); singleton.setAccessible(true); singleton.set(null, null);
            store = MobileLibrary.get(context);
            require(current.getName().equals(pin(store, id).getString("mediaFile")), "Storage reopen lost the prior preview.");

            String other = store.add(collection, "https://example.com/second");
            store.captured(other, new JSONObject().put("mediaFile", current.getName()).put("kind", "image").put("capture", "saved"));
            store.removePin(other);
            require(current.isFile(), "Removing one pin deleted another pin's shared preview.");
            other = store.add(collection, "https://example.com/second");
            store.captured(other, new JSONObject().put("mediaFile", current.getName()).put("kind", "image").put("capture", "saved"));
            store.captured(id, new JSONObject().put("mediaFile", replacement.getName()).put("capture", "saved"));
            require(current.isFile(), "Replacing a preview deleted another pin's shared file.");
            store.removePin(other);
            require(!current.exists() && replacement.isFile(), "Removing the last reference left its file or removed another preview.");
            store.removeCollection(collection);
            require(!replacement.exists() && store.snapshot().getJSONArray("pins").length() == 0, "Collection deletion left its cached preview.");
            require(new File(context.getFilesDir(), "mobile-media").list().length == 0, "Unused preview files remain.");
            captureRollback(store, context, database);
            captureVideos(store, context);
            selectedStorage(store, context, database);
            moveStorage(store, context, database);
            require(new File(context.getFilesDir(), "mobile-media").list().length == 0, "Recapture cleanup left media behind.");
            result.putString("stream", "Mobile storage checks passed: replacement, retained cache, failed commit, reopen, shared ownership, cleanup and HTTP recapture rollback/retry; seven direct/OG/Twitter/video/source/base/redirect/signed-URL video saves; failed-video retention/retry; invalid-scheme and oversized video poster fallback/cleanup; selected attachment deduplication, legacy pin upgrade and cover, atomic commit rollback, shared attachment ownership and cleanup.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "Mobile storage check failed: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
