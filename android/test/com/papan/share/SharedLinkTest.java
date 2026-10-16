package com.papan.share;

public final class SharedLinkTest {
    private static void equal(String expected, String input) throws Exception {
        String actual = SharedLink.parse(input);
        if (!expected.equals(actual)) throw new AssertionError("Shared URL changed: expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) throws Exception {
        for (String url : new String[] {"https://example.com/post!", "https://example.com/post.", "https://example.com/post,", "https://example.com/post;", "https://example.com/?key=value!", "https://example.com/?key=value;"}) equal(url, url);
        equal("https://example.com/post!", "  https://example.com/post!  ");
        equal("https://example.com/post", "Read this: https://example.com/post.");
        for (String input : new String[] {null, "file:///private", "javascript:alert(1)", "https://user:password@example.com", "no link here"}) {
            boolean rejected = false;
            try { SharedLink.parse(input); } catch (Exception error) { rejected = true; }
            if (!rejected) throw new AssertionError("Invalid shared input was accepted.");
        }
        System.out.println("Shared-link checks passed: 8 preserved/extracted URLs and 5 invalid inputs.");
    }
}
