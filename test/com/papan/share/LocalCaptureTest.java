package com.papan.share;

import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalCaptureTest {
    public static void main(String[] args) throws Exception {
        AtomicReference<String> type = new AtomicReference<>();
        byte[] bytes = new byte[] {1, 2, 3, 4};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/media", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", type.get());
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
            finally { exchange.close(); }
        });
        server.createContext("/page", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            byte[] page = new byte[1024 * 1024 + (query.contains("oversized") ? 1 : 0)];
            Arrays.fill(page, (byte)'x');
            exchange.getResponseHeaders().set("Content-Type", "application/xhtml+xml; charset=utf-8");
            exchange.sendResponseHeaders(200, query.contains("chunked") ? 0 : page.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(page); }
            catch (java.io.IOException ignored) { /* Oversized responses may be closed before they finish sending. */ }
            finally { exchange.close(); }
        });
        server.start();
        try {
            Method fetch = LocalCapture.class.getDeclaredMethod("fetch", String.class, int.class); fetch.setAccessible(true);
            Method extension = LocalCapture.class.getDeclaredMethod("extension", String.class); extension.setAccessible(true);
            for (String[] sample : new String[][] {
                {"Image/PNG \t; charset=binary", "image/png", "png"},
                {"video/mp4 ; codecs=avc1", "video/mp4", "mp4"},
                {"text/html ; charset=UTF-8", "text/html", ""},
                {"image/jpeg", "image/jpeg", "jpg"},
                {"application/pdf ; version=1.7", "application/pdf", ""}
            }) {
                type.set(sample[0]);
                Object content = fetch.invoke(null, "http://127.0.0.1:" + server.getAddress().getPort() + "/media", 1024);
                Field mediaType = content.getClass().getDeclaredField("type"); mediaType.setAccessible(true);
                Field body = content.getClass().getDeclaredField("bytes"); body.setAccessible(true);
                String actual = (String)mediaType.get(content);
                if (!sample[1].equals(actual)) throw new AssertionError("Unexpected media type: " + actual);
                if (!sample[2].equals(extension.invoke(null, actual))) throw new AssertionError("Unexpected saved media extension.");
                if (!Arrays.equals(bytes, (byte[])body.get(content))) throw new AssertionError("Downloaded bytes changed.");
            }
            for (boolean chunked : new boolean[] {false, true}) for (boolean oversized : new boolean[] {false, true}) {
                String query = (chunked ? "chunked" : "length") + (oversized ? "&oversized" : "");
                try {
                    Object content = fetch.invoke(null, "http://127.0.0.1:" + server.getAddress().getPort() + "/page?" + query, 32 * 1024 * 1024);
                    if (oversized) throw new AssertionError("XHTML page exceeded the HTML preview limit: " + query);
                    Field body = content.getClass().getDeclaredField("bytes"); body.setAccessible(true);
                    byte[] actual = (byte[])body.get(content);
                    if (actual.length != 1024 * 1024 || actual[actual.length - 1] != 'x') throw new AssertionError("Boundary-sized XHTML page changed.");
                } catch (InvocationTargetException error) {
                    if (!oversized || !error.getCause().getMessage().contains("too large")) throw error;
                }
            }
            System.out.println("Local-capture HTTP checks passed: 5 media types and 4 XHTML size-boundary cases with fixed/chunked responses.");
        } finally { server.stop(0); }
    }
}
