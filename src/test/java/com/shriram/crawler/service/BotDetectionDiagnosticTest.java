package com.shriram.crawler.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class BotDetectionDiagnosticTest {

    private static final String TEST_URL =
            "http://blog.rei.com/camp/how-to-introduce-your-indoorsy-friend-to-the-outdoors/";

    @Test
    void diagnoseWithMinimalHeaders() {
        System.out.println("=== Test 1: Minimal headers (most likely to be detected as bot) ===");
        tryConnect(Jsoup.connect(TEST_URL));
    }

    @Test
    void diagnoseWithUserAgentOnly() {
        System.out.println("=== Test 2: User-Agent only ===");
        tryConnect(Jsoup.connect(TEST_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));
    }

    @Test
    void diagnoseWithFullBrowserHeaders() {
        System.out.println("=== Test 3: Full browser-like headers ===");
        tryConnect(Jsoup.connect(TEST_URL)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .referrer("https://www.google.com/"));
    }

    @Test
    void diagnoseWithJsoupBotAgent() {
        System.out.println("=== Test 4: Obvious bot User-Agent ===");
        tryConnect(Jsoup.connect(TEST_URL)
                .userAgent("Jsoup/1.18.3 Bot"));
    }

    private void tryConnect(Connection connection) {
        long start = System.currentTimeMillis();
        try {
            Connection.Response response = connection
                    .timeout(60_000)
                    .followRedirects(true)
                    .execute();

            long elapsed = System.currentTimeMillis() - start;

            System.out.println("  Status code   : " + response.statusCode());
            System.out.println("  Status message : " + response.statusMessage());
            System.out.println("  Content type   : " + response.contentType());
            System.out.println("  Body length    : " + response.body().length() + " chars");
            System.out.println("  Time taken     : " + elapsed + " ms");
            System.out.println("  Final URL      : " + response.url());

            String body = response.body().toLowerCase();
            boolean hasCaptcha = body.contains("captcha") || body.contains("robot")
                    || body.contains("are you a human") || body.contains("access denied")
                    || body.contains("blocked") || body.contains("challenge");
            System.out.println("  Bot indicators : " + (hasCaptcha ? "YES - possible bot block" : "NONE"));

            System.out.println("  Response headers:");
            response.headers().forEach((k, v) -> System.out.println("    " + k + ": " + v));

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("  FAILED after " + elapsed + "ms");
            System.out.println("  Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        System.out.println();
    }
}
