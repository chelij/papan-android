package com.papan.share;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SharedLink {
    public static String parse(String text) throws Exception {
        if (text == null || text.length() > 16384) throw new Exception("Share a web link.");
        Matcher match = Pattern.compile("https?://[^\\s<>]+", Pattern.CASE_INSENSITIVE).matcher(text);
        if (!match.find()) throw new Exception("No web link was found in the shared text.");
        String url = match.group();
        if (!text.trim().equals(url)) url = url.replaceAll("[.,;!]+$", "");
        URI parsed = new URI(url);
        if (url.length() > 8192 || parsed.getHost() == null || parsed.getUserInfo() != null) throw new Exception("Share a valid HTTP or HTTPS link without embedded credentials.");
        return url;
    }
}
