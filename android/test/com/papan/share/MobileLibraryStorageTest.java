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
            require(new File(context.getFilesDir(), "mobile-media").list().length == 0, "Recapture cleanup left media behind.");
            result.putString("stream", "Mobile storage checks passed: replacement, retained cache, failed commit, reopen, shared ownership, final cleanup, actual HTTP recapture rollback, and successful retry.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "Mobile storage check failed: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
