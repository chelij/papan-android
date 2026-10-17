package com.papan.share;

// HTTP-only JVM tests cannot launch Android's native Python process; native tests cover it.
final class MediaExtractor {
    static Object run(android.content.Context context, org.json.JSONObject request, java.util.concurrent.atomic.AtomicBoolean cancelled) {
        throw new UnsupportedOperationException("Use native emulator checks for selected-media extraction.");
    }
}
