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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalCapture {
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final java.util.concurrent.ExecutorService captures = java.util.concurrent.Executors.newSingleThreadExecutor();
    public static void submit(Context context, java.util.function.Consumer<Exception> finished) { captures.execute(() -> { Exception failure = null; try { sync(context); } catch (Exception error) { failure = error; } finished.accept(failure); }); }
    public static String webLink(String text) throws Exception {
        return SharedLink.parse(text);
    }
    private static URI validate(String value) throws Exception {
        URI uri = new URI(value);
        if (value.length() > 8192 || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) throw new Exception("Use a valid HTTP or HTTPS link without embedded credentials.");
        return uri;
    }
    private static final class Content { String type, url; byte[] bytes; }
    private static Content fetch(String value, int maximum) throws Exception {
        return fetch(value, maximum, null);
    }
    static byte[] preview(String url) throws Exception { Content content = fetch(url, 8 * 1024 * 1024); if (!content.type.startsWith("image/")) throw new Exception("Preview unavailable."); return content.bytes; }
    private static Content fetch(String value, int maximum, String page) throws Exception {
        URI referrer = page == null ? null : validate(page);
        for (int redirect = 0; redirect < 5; redirect++) {
            URI uri = validate(value); HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false); connection.setRequestProperty("User-Agent", "Papan-Android/0.3"); connection.setRequestProperty("Accept-Encoding", "identity");
            if (referrer != null && !(referrer.getScheme().equalsIgnoreCase("https") && uri.getScheme().equalsIgnoreCase("http"))) {
                boolean sameOrigin = referrer.getScheme().equalsIgnoreCase(uri.getScheme()) && referrer.getRawAuthority().equalsIgnoreCase(uri.getRawAuthority());
                connection.setRequestProperty("Referer", sameOrigin ? page.split("#", 2)[0] : referrer.getScheme() + "://" + referrer.getRawAuthority() + "/");
            }
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) { String location = connection.getHeaderField("Location"); if (location == null) throw new Exception("Missing redirect address."); value = uri.resolve(location).toString(); continue; }
                if (status != 200) throw new Exception("Source returned HTTP " + status + ".");
                Content result = new Content(); result.type = connection.getContentType() == null ? "" : connection.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT); result.url = value;
                int limit = result.type.startsWith("text/") || result.type.equals("application/xhtml+xml") ? Math.min(maximum, 1024 * 1024) : maximum;
                if (connection.getContentLengthLong() > limit) throw new Exception("Media is too large to save on the phone.");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int size; while ((size = input.read(buffer)) != -1) { if (bytes.size() + size > limit) throw new Exception("Source is too large to preview on the phone."); bytes.write(buffer, 0, size); } result.bytes = bytes.toByteArray(); }
                return result;
            } finally { connection.disconnect(); }
        }
        throw new Exception("Too many source redirects.");
    }
    private static String plain(String value) { return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim(); }
    private static String attribute(String tag, String name) {
        Matcher attributes = Pattern.compile("([a-zA-Z][a-zA-Z0-9:_-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", Pattern.DOTALL).matcher(tag);
        while (attributes.find()) if (name.equalsIgnoreCase(attributes.group(1))) return Html.fromHtml(attributes.group(2) != null ? attributes.group(2) : attributes.group(3) != null ? attributes.group(3) : attributes.group(4), Html.FROM_HTML_MODE_LEGACY).toString().trim();
        return "";
    }
    private static String meta(String html, String name) {
        Matcher tags = Pattern.compile("<meta\\s+[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (tags.find()) {
            String tag = tags.group(), key = attribute(tag, "property"); if (key.isEmpty()) key = attribute(tag, "name");
            if (name.equalsIgnoreCase(key)) return attribute(tag, "content");
        }
        return "";
    }
    private static Set<String> videos(String html) {
        Set<String> values = new LinkedHashSet<>();
        for (String key : new String[] {"og:video:secure_url", "og:video:url", "og:video", "twitter:player:stream"}) { String value = meta(html, key); if (!value.isEmpty()) values.add(value); }
        Matcher tags = Pattern.compile("<video\\b[^>]*>(?:.*?</video\\s*>)?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        while (tags.find() && values.size() < 8) {
            String video = tags.group(), src = attribute(video.substring(0, video.indexOf('>') + 1), "src"); if (!src.isEmpty()) values.add(src);
            Matcher sources = Pattern.compile("<source\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(video);
            while (sources.find() && values.size() < 8) { src = attribute(sources.group(), "src"); if (!src.isEmpty()) values.add(src); }
        }
        return values;
    }
    private static String extension(String type) {
        switch (type) { case "image/jpeg": return "jpg"; case "image/png": return "png"; case "image/webp": return "webp"; case "image/gif": return "gif"; case "video/mp4": return "mp4"; case "video/webm": return "webm"; default: return ""; }
    }
    private static void selected(Context context, MobileLibrary store, JSONObject pin) throws Exception {
        String engine = pin.getString("engine");
        JSONObject inspection = "direct".equals(engine) ? (JSONObject)MediaExtractor.run(context, new JSONObject().put("action", "inspect").put("url", pin.getString("sourceUrl")).put("engine", engine), new AtomicBoolean(false)) : new JSONObject().put("engine", engine).put("items", pin.getJSONArray("items"));
        JSONArray chosen = pin.getJSONArray("items"), candidates = inspection.getJSONArray("items"), ready = new JSONArray();
        if (chosen.length() == 0 || chosen.length() > 50) throw new Exception("Choose between 1 and 50 media items.");
        java.util.List<File> newFiles = new java.util.ArrayList<>(); File directory = java.nio.file.Files.createTempDirectory(context.getCacheDir().toPath(), "selected-media-").toFile();
        boolean committed = false;
        try {
            JSONArray batch = null;
            if (!"direct".equals(engine)) { JSONArray keys = new JSONArray(); for (int i = 0; i < chosen.length(); i++) keys.put(chosen.getJSONObject(i).getString("key")); batch = (JSONArray)MediaExtractor.run(context, new JSONObject().put("action", "download").put("url", pin.getString("sourceUrl")).put("engine", engine).put("keys", keys).put("items", chosen).put("output", directory.toString()), new AtomicBoolean(false)); if (batch.length() != chosen.length()) throw new Exception("The provider returned a different media selection."); }
            for (int i = 0; i < chosen.length(); i++) {
                JSONObject item = chosen.getJSONObject(i), source = null;
                for (int j = 0; j < candidates.length(); j++) if (item.getString("key").equals(candidates.getJSONObject(j).optString("key"))) source = candidates.getJSONObject(j);
                if (source == null || !item.getString("kind").equals(source.optString("kind"))) throw new Exception("The selected media changed. Paste the link again to choose media.");
                File output = directory; JSONArray result = new JSONArray();
                if (batch != null) { for (int j = 0; j < batch.length(); j++) if (item.getString("key").equals(batch.getJSONObject(j).optString("key"))) result.put(batch.getJSONObject(j)); }
                else { output = new File(directory, Integer.toString(i)); if (!output.mkdir()) throw new Exception("Could not prepare the download."); JSONObject request = new JSONObject().put("action", "download").put("url", pin.getString("sourceUrl")).put("engine", engine).put("keys", new JSONArray().put(item.getString("key"))).put("item", source).put("output", output.toString()); result = (JSONArray)MediaExtractor.run(context, request, new AtomicBoolean(false)); }
                if (result.length() != 1 || !item.getString("key").equals(result.getJSONObject(0).optString("key"))) throw new Exception("The provider returned different media.");
                File downloaded = new File(result.getJSONObject(0).getString("file")).getCanonicalFile();
                if (!downloaded.toPath().startsWith(output.getCanonicalFile().toPath()) || !downloaded.isFile() || downloaded.length() == 0 || downloaded.length() > 32L * 1024 * 1024) throw new Exception("The selected media could not be saved within 32 MiB.");
                String ext = downloaded.getName().replaceFirst("^.*\\.", "").toLowerCase(Locale.ROOT);
                if (!("video".equals(item.getString("kind")) ? ext.matches("mp4|webm") : ext.matches("jpg|png|webp|gif"))) throw new Exception("The provider returned an unsupported media format.");
                if ("video".equals(item.getString("kind"))) { android.media.MediaMetadataRetriever decoder = new android.media.MediaMetadataRetriever(); try { decoder.setDataSource(downloaded.toString()); if (Integer.parseInt(decoder.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)) <= 0) throw new Exception("The source did not return a playable video."); } finally { decoder.release(); } }
                else { android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options(); bounds.inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeFile(downloaded.toString(), bounds); if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new Exception("The source did not return an image."); }
                String filename = UUID.nameUUIDFromBytes((pin.getString("id") + ":" + item.getString("key") + ":" + item.optString("mediaFile") + ":" + ext).getBytes(StandardCharsets.UTF_8)) + "." + ext;
                File saved = store.mediaFile(filename); if (!saved.exists()) newFiles.add(saved); AtomicFile media = new AtomicFile(saved); FileOutputStream stream = null;
                try (InputStream input = new java.io.FileInputStream(downloaded)) { stream = media.startWrite(); byte[] buffer = new byte[65536]; int count; while ((count = input.read(buffer)) != -1) stream.write(buffer, 0, count); media.finishWrite(stream); }
                catch (Exception error) { media.failWrite(stream); throw error; }
                ready.put(new JSONObject(item.toString()).put("mediaFile", filename));
            }
            committed = store.captured(pin.getString("id"), new JSONObject().put("items", ready).put("capture", "saved").put("captureError", ""));
        } finally {
            if (!committed) for (File file : newFiles) file.delete();
            try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(directory.toPath())) { for (java.nio.file.Path path : (Iterable<java.nio.file.Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator) java.nio.file.Files.deleteIfExists(path); }
        }
    }
    public static void sync(Context context) throws Exception {
        if (!running.compareAndSet(false, true)) return;
        try {
            MobileLibrary store = MobileLibrary.get(context); JSONArray pins = store.snapshot().getJSONArray("pins");
            for (int i = 0; i < pins.length(); i++) {
                JSONObject pin = pins.getJSONObject(i); if (!"pending".equals(pin.optString("capture"))) continue;
                String id = pin.getString("id"); File saved = null; boolean savedWasNew = false;
                try {
                    if (pin.has("items") && pin.has("engine")) { selected(context, store, pin); continue; }
                    Content source = fetch(pin.getString("sourceUrl"), 32 * 1024 * 1024); JSONObject details = new JSONObject().put("capture", "saved").put("captureError", "");
                    if (source.type.equals("text/html") || source.type.equals("application/xhtml+xml")) {
                        String html = new String(source.bytes, StandardCharsets.UTF_8), title = meta(html, "og:title");
                        if (title.isEmpty()) { Matcher tag = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html); if (tag.find()) title = plain(tag.group(1)); }
                        title = plain(title); if (!title.isEmpty()) details.put("title", title.substring(0, Math.min(200, title.length())));
                        String page = source.url; URI base = new URI(page);
                        Matcher baseTag = Pattern.compile("<base\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
                        if (baseTag.find()) { String href = attribute(baseTag.group(), "href"); if (!href.isEmpty()) try { base = validate(base.resolve(href).toString()); } catch (Exception ignored) {} }
                        Set<String> candidates = videos(html); Content video = null; Exception videoError = null;
                        for (String candidate : candidates) try { Content media = fetch(base.resolve(candidate).toString(), 32 * 1024 * 1024, page); if (!media.type.startsWith("video/") || extension(media.type).isEmpty()) throw new Exception("This page does not provide a downloadable MP4 or WebM video."); video = media; break; } catch (Exception error) { videoError = error; }
                        String image = meta(html, "og:image"); if (image.isEmpty()) image = meta(html, "twitter:image");
                        if (video != null) source = video;
                        else {
                            if (!candidates.isEmpty()) details.put("capture", "failed").put("captureError", "Link saved. Video could not be saved. " + videoError.getMessage());
                            if (!image.isEmpty() && !"video".equals(pin.optString("kind"))) source = fetch(base.resolve(image).toString(), 8 * 1024 * 1024, page);
                        }
                    }
                    String ext = extension(source.type);
                    if (!ext.isEmpty()) {
                        // Keep the current cache intact and reuse an uncommitted replacement across retries.
                        String filename = UUID.nameUUIDFromBytes((id + ":" + pin.optString("mediaFile") + ":" + ext).getBytes(StandardCharsets.UTF_8)) + "." + ext; saved = store.mediaFile(filename); savedWasNew = !saved.exists(); AtomicFile media = new AtomicFile(saved); FileOutputStream stream = null;
                        try { stream = media.startWrite(); stream.write(source.bytes); media.finishWrite(stream); } catch (Exception error) { media.failWrite(stream); throw error; }
                        details.put("mediaFile", filename).put("kind", source.type.startsWith("video/") ? "video" : "image");
                    }
                    if (!store.captured(id, details) && saved != null) saved.delete();
                } catch (Exception error) { if (saved != null && savedWasNew) saved.delete(); store.captured(id, new JSONObject().put("capture", "failed").put("captureError", "Link saved. " + (error.getMessage() == null ? "Preview is unavailable." : error.getMessage()))); }
            }
        } finally { running.set(false); }
    }
}
