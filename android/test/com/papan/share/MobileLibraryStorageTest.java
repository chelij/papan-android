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
            result.putString("stream", "Mobile storage checks passed: replacement, retained cache, failed commit, reopen, shared ownership, and final cleanup.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "Mobile storage check failed: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
