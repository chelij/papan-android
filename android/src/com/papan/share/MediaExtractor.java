package com.papan.share;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class MediaExtractor {
    private static synchronized File prepare(Context context) throws Exception {
        com.yausername.youtubedl_android.YoutubeDL.getInstance().init(context);
        com.yausername.ffmpeg.FFmpeg.getInstance().init(context);
        File root = new File(context.getNoBackupFilesDir(), "papan-media-runtime");
        if (!root.isDirectory() && !root.mkdirs()) throw new Exception("Could not prepare media downloads.");
        for (String name : new String[] {"desktop_extract.py", "mobile_extract.py", "packages.zip"}) {
            byte[] bytes; try (InputStream input = context.getAssets().open("media-runtime/" + name)) { bytes = read(input, 24 * 1024 * 1024); }
            File target = new File(root, name);
            if (!target.isFile() || !java.util.Arrays.equals(bytes, Files.readAllBytes(target.toPath()))) {
                File temporary = new File(root, name + ".new"); Files.write(temporary.toPath(), bytes); Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return root;
    }
    private static byte[] read(InputStream input, int maximum) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) { if (output.size() + count > maximum) throw new Exception("The media provider returned too much data."); output.write(buffer, 0, count); }
        return output.toByteArray();
    }
    static Object run(Context context, JSONObject request, AtomicBoolean cancelled) throws Exception {
        File root = prepare(context), nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File packages = new File(context.getNoBackupFilesDir(), "youtubedl-android/packages"), python = new File(packages, "python/usr");
        request.put("ffmpeg", new File(nativeDir, "libffmpeg.so").toString());
        ProcessBuilder builder = new ProcessBuilder(new File(nativeDir, "libpython.so").toString(), "-s", new File(root, "mobile_extract.py").toString());
        builder.environment().put("PYTHONHOME", python.toString()); builder.environment().put("PYTHONNOUSERSITE", "1"); builder.environment().put("NETRC", "/dev/null");
        builder.environment().put("LD_LIBRARY_PATH", new File(python, "lib") + ":" + new File(packages, "ffmpeg/usr/lib"));
        builder.environment().put("SSL_CERT_FILE", new File(python, "etc/tls/cert.pem").toString()); builder.environment().put("TMPDIR", context.getCacheDir().toString());
        builder.environment().put("PATH", nativeDir + ":/system/bin");
        Process process = builder.start(); byte[][] output = new byte[1][]; Exception[] problem = new Exception[1];
        Thread reader = new Thread(() -> { try { output[0] = read(process.getInputStream(), 2 * 1024 * 1024); } catch (Exception error) { problem[0] = error; process.destroy(); } });
        Thread errors = new Thread(() -> { try (InputStream input = process.getErrorStream()) { byte[] buffer = new byte[4096]; while (input.read(buffer) != -1) {} } catch (Exception ignored) {} });
        reader.start(); errors.start();
        try {
            try (java.io.OutputStream input = process.getOutputStream()) { input.write(request.toString().getBytes(StandardCharsets.UTF_8)); }
            long deadline = android.os.SystemClock.elapsedRealtime() + ("inspect".equals(request.optString("action")) ? 90000 : 180000);
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new Exception("Cancelled.");
                if (android.os.SystemClock.elapsedRealtime() > deadline) throw new Exception("The media provider took too long. Try again later.");
            }
            reader.join(3000); if (problem[0] != null) throw problem[0];
            if (output[0] == null) throw new Exception("Could not read the media provider response.");
            JSONObject response = new JSONObject(new String(output[0], StandardCharsets.UTF_8));
            if (!response.optBoolean("ok")) throw new Exception(response.optString("error", "This media could not be downloaded."));
            return response.get("result");
        } finally { process.destroyForcibly(); reader.join(3000); errors.join(3000); }
    }
}
