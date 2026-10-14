package com.papan.share;

import android.content.Context;
import android.text.Html;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalCapture {
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final java.util.concurrent.ExecutorService captures = java.util.concurrent.Executors.newSingleThreadExecutor();
    public static void submit(Context context, java.util.function.Consumer<Exception> finished) { captures.execute(() -> { Exception failure = null; try { sync(context); } catch (Exception error) { failure = error; } finished.accept(failure); }); }
    public static String webLink(String text) throws Exception {
        if (text == null || text.length() > 16384) throw new Exception("Paste or share a web link.");
        Matcher found = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(text);
        if (!found.find()) throw new Exception("No web link was found.");
        String url = found.group().replaceAll("[.,;!]+$", ""); validate(url); return url;
    }
    private static URI validate(String value) throws Exception {
        URI uri = new URI(value);
        if (value.length() > 8192 || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) throw new Exception("Use a valid HTTP or HTTPS link without embedded credentials.");
        return uri;
    }
    private static final class Content { String type, url; byte[] bytes; }
    private static Content fetch(String value, int maximum) throws Exception {
        for (int redirect = 0; redirect < 5; redirect++) {
            URI uri = validate(value); HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false); connection.setRequestProperty("User-Agent", "Papan-Android/0.3"); connection.setRequestProperty("Accept-Encoding", "identity");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) { String location = connection.getHeaderField("Location"); if (location == null) throw new Exception("Missing redirect address."); value = uri.resolve(location).toString(); continue; }
                if (status != 200) throw new Exception("Source returned HTTP " + status + ".");
                Content result = new Content(); result.type = connection.getContentType() == null ? "" : connection.getContentType().split(";")[0].toLowerCase(Locale.ROOT); result.url = value;
                int limit = result.type.startsWith("text/") ? Math.min(maximum, 1024 * 1024) : maximum;
                if (connection.getContentLengthLong() > limit) throw new Exception("Media is too large to save on the phone.");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int size; while ((size = input.read(buffer)) != -1) { if (bytes.size() + size > limit) throw new Exception("Source is too large to preview on the phone."); bytes.write(buffer, 0, size); } result.bytes = bytes.toByteArray(); }
                return result;
            } finally { connection.disconnect(); }
        }
        throw new Exception("Too many source redirects.");
    }
    private static String plain(String value) { return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim(); }
    private static String meta(String html, String name) {
        Matcher tags = Pattern.compile("<meta\\s+[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (tags.find()) {
            String key = null, value = null; Matcher attributes = Pattern.compile("([a-zA-Z:-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.DOTALL).matcher(tags.group());
            while (attributes.find()) { if (attributes.group(1).equalsIgnoreCase("property") || attributes.group(1).equalsIgnoreCase("name")) key = attributes.group(3); if (attributes.group(1).equalsIgnoreCase("content")) value = attributes.group(3); }
            if (name.equalsIgnoreCase(key) && value != null) return plain(value);
        }
        return "";
    }
    private static String extension(String type) {
        switch (type) { case "image/jpeg": return "jpg"; case "image/png": return "png"; case "image/webp": return "webp"; case "image/gif": return "gif"; case "video/mp4": return "mp4"; case "video/webm": return "webm"; default: return ""; }
    }
    public static void sync(Context context) throws Exception {
        if (!running.compareAndSet(false, true)) return;
        try {
            MobileLibrary store = MobileLibrary.get(context); JSONArray pins = store.snapshot().getJSONArray("pins");
            for (int i = 0; i < pins.length(); i++) {
                JSONObject pin = pins.getJSONObject(i); if (!"pending".equals(pin.optString("capture"))) continue;
                String id = pin.getString("id"); File saved = null; boolean savedWasNew = false;
                try {
                    Content source = fetch(pin.getString("sourceUrl"), 32 * 1024 * 1024); JSONObject details = new JSONObject().put("capture", "saved").put("captureError", "");
                    if (source.type.equals("text/html") || source.type.equals("application/xhtml+xml")) {
                        String html = new String(source.bytes, StandardCharsets.UTF_8), title = meta(html, "og:title");
                        if (title.isEmpty()) { Matcher tag = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html); if (tag.find()) title = plain(tag.group(1)); }
                        if (!title.isEmpty()) details.put("title", title.substring(0, Math.min(200, title.length())));
                        String image = meta(html, "og:image"); if (image.isEmpty()) image = meta(html, "twitter:image");
                        if (!image.isEmpty()) source = fetch(new URI(source.url).resolve(image).toString(), 8 * 1024 * 1024);
                    }
                    String ext = extension(source.type);
                    if (!ext.isEmpty()) {
                        String filename = id + "." + ext; saved = store.mediaFile(filename); savedWasNew = !saved.exists(); AtomicFile media = new AtomicFile(saved); FileOutputStream stream = null;
                        try { stream = media.startWrite(); stream.write(source.bytes); media.finishWrite(stream); } catch (Exception error) { media.failWrite(stream); throw error; }
                        details.put("mediaFile", filename).put("kind", source.type.startsWith("video/") ? "video" : "image");
                    }
                    if (!store.captured(id, details) && saved != null) saved.delete();
                } catch (Exception error) { if (saved != null && savedWasNew) saved.delete(); store.captured(id, new JSONObject().put("capture", "failed").put("captureError", "Link saved. " + (error.getMessage() == null ? "Preview is unavailable." : error.getMessage()))); }
            }
        } finally { running.set(false); }
    }
}
