package com.shriram.crawler.service;

import com.shriram.crawler.dto.CrawlRequest;
import com.shriram.crawler.dto.CrawlResponse;
import com.shriram.crawler.dto.PageMetadata;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CrawlerService {

    /** Default when `crawler.http.timeout-ms` is not set (matches `application.properties`). */
    private static final int DEFAULT_HTTP_TIMEOUT_MS = 30_000;

    private static final int MAX_BODY_SIZE = 524_288; // 512KB - enough for metadata + 5000 chars of body
    private static final int MAX_BODY_LENGTH = 5000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Map<String, List<String>> TOPIC_DICTIONARY = buildTopicDictionary();

    private final ExecutorService crawlerExecutor;
    private final int httpTimeoutMs;

    public CrawlerService(
            @Qualifier("crawlerExecutor") ExecutorService crawlerExecutor,
            @Value("${crawler.http.timeout-ms:" + DEFAULT_HTTP_TIMEOUT_MS + "}") int httpTimeoutMs) {
        this.crawlerExecutor = crawlerExecutor;
        this.httpTimeoutMs = httpTimeoutMs;
    }

    @PostConstruct
    void initSsl() {
        try {
            TrustManager[] trustAll = {new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String type) {}
                public void checkServerTrusted(X509Certificate[] certs, String type) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            log.info("SSL validation disabled for crawler");
        } catch (Exception e) {
            log.warn("Could not disable SSL validation", e);
        }
    }

    public CrawlResponse crawl(CrawlRequest request) {
        long start = System.currentTimeMillis();

        List<Future<PageMetadata>> futures = request.getUrls().stream()
                .map(url -> crawlerExecutor.submit(() -> extractMetadata(url)))
                .toList();

        List<PageMetadata> results = futures.stream()
                .map(this::getResult)
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        long successCount = results.stream().filter(PageMetadata::isSuccess).count();
        log.info("Crawled {} URLs in {}ms (parallel)", results.size(), elapsed);

        return CrawlResponse.builder()
                .totalRequested(results.size())
                .totalSuccessful((int) successCount)
                .timeTakenMs(elapsed)
                .results(results)
                .build();
    }

    private PageMetadata getResult(Future<PageMetadata> future) {
        try {
            return future.get();
        } catch (Exception e) {
            log.error("Crawl task failed", e);
            return PageMetadata.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private PageMetadata extractMetadata(String url) {
        log.info("Crawling {} on thread: {}", url, Thread.currentThread());

        if (!isValidUrl(url)) {
            return PageMetadata.builder()
                    .url(url)
                    .success(false)
                    .errorMessage("Invalid URL format")
                    .build();
        }

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(httpTimeoutMs)
                    .maxBodySize(MAX_BODY_SIZE)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .referrer("https://www.google.com/")
                    .followRedirects(true)
                    .execute();

            int statusCode = response.statusCode();
            // 2xx = success per HTTP (not only 200 — e.g. 201/204 are valid). 4xx/5xx and non-2xx fail.
            if (statusCode < 200 || statusCode > 299) {
                return PageMetadata.builder()
                        .url(url)
                        .success(false)
                        .errorMessage("HTTP " + statusCode + " - " + response.statusMessage())
                        .build();
            }

            String contentType = response.contentType();
            if (contentType != null && !contentType.contains("html") && !contentType.contains("xml")) {
                return PageMetadata.builder()
                        .url(url)
                        .success(false)
                        .errorMessage("Unsupported content type: " + contentType)
                        .build();
            }

            Document doc = response.parse();

            String title = doc.title();
            String description = getMetaContent(doc, "description");
            String bodyText = extractBodyText(doc);
            List<String> topics = classifyTopics(title, description, bodyText);

            return PageMetadata.builder()
                    .url(url)
                    .title(title)
                    .description(description)
                    .bodyText(bodyText)
                    .topics(topics)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to crawl URL: {}", url, e);
            return PageMetadata.builder()
                    .url(url)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private boolean isValidUrl(String url) {
        try {
            URI.create(url).toURL();
            return true;
        } catch (MalformedURLException | IllegalArgumentException e) {
            return false;
        }
    }

    private String getMetaContent(Document doc, String name) {
        Element meta = doc.selectFirst("meta[name=" + name + "]");
        if (meta != null) {
            return meta.attr("content");
        }
        meta = doc.selectFirst("meta[property=" + name + "]");
        return meta != null ? meta.attr("content") : "";
    }

    private String extractBodyText(Document doc) {
        String text = doc.body() != null ? doc.body().text() : "";
        if (text.length() > MAX_BODY_LENGTH) {
            return text.substring(0, MAX_BODY_LENGTH) + "...";
        }
        return text;
    }

    private List<String> classifyTopics(String title, String description, String bodyText) {
        String titleLower = title != null ? title.toLowerCase() : "";
        String descLower = description != null ? description.toLowerCase() : "";
        String bodyLower = bodyText != null ? bodyText.toLowerCase() : "";

        Map<String, Integer> scores = new LinkedHashMap<>();
        for (var entry : TOPIC_DICTIONARY.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (titleLower.contains(keyword)) score += 3;
                else if (descLower.contains(keyword)) score += 2;
                else if (bodyLower.contains(keyword)) score += 1;
            }
            if (score > 0) {
                scores.put(entry.getKey(), score);
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static Map<String, List<String>> buildTopicDictionary() {
        Map<String, List<String>> topics = new LinkedHashMap<>();

        topics.put("Electronics", List.of(
                "electronic", "gadget", "device", "battery", "charger", "usb", "bluetooth",
                "wireless", "speaker", "headphone", "laptop", "tablet", "phone", "camera"));
        topics.put("Kitchen & Home Appliances", List.of(
                "kitchen", "toaster", "blender", "oven", "microwave", "cookware", "appliance",
                "cuisinart", "coffee", "maker", "refrigerator", "dishwasher", "mixer"));
        topics.put("Technology", List.of(
                "software", "hardware", "computer", "programming", "code", "developer",
                "api", "cloud", "server", "database", "algorithm", "machine learning", "ai"));
        topics.put("E-Commerce", List.of(
                "buy", "price", "cart", "shop", "deal", "discount", "order", "shipping",
                "delivery", "checkout", "add to cart", "wishlist", "seller", "marketplace"));
        topics.put("Health & Wellness", List.of(
                "health", "fitness", "nutrition", "vitamin", "exercise", "wellness",
                "medical", "supplement", "diet", "workout", "yoga", "organic"));
        topics.put("Fashion & Clothing", List.of(
                "fashion", "clothing", "dress", "shirt", "shoes", "accessories", "jewelry",
                "apparel", "wear", "style", "designer", "brand"));
        topics.put("Sports & Outdoors", List.of(
                "sport", "outdoor", "hiking", "camping", "bicycle", "running", "gym",
                "fishing", "athletic", "equipment", "ball", "team"));
        topics.put("Books & Education", List.of(
                "book", "education", "learn", "course", "university", "school", "study",
                "textbook", "author", "reading", "library", "academic"));
        topics.put("News & Media", List.of(
                "news", "article", "breaking", "report", "journalist", "media",
                "headline", "press", "editorial", "opinion", "politics"));
        topics.put("Food & Beverage", List.of(
                "food", "recipe", "restaurant", "cooking", "meal", "drink", "beverage",
                "ingredient", "cuisine", "chef", "dining", "snack"));
        topics.put("Automotive", List.of(
                "car", "vehicle", "auto", "motor", "engine", "tire", "drive",
                "truck", "suv", "sedan", "fuel", "electric vehicle"));
        topics.put("Entertainment", List.of(
                "movie", "music", "game", "streaming", "video", "entertainment",
                "concert", "show", "television", "series", "podcast", "album"));
        topics.put("Home & Garden", List.of(
                "furniture", "decor", "garden", "home improvement", "diy", "interior",
                "bedroom", "bathroom", "living room", "paint", "tool", "hardware"));
        topics.put("Product Reviews", List.of(
                "review", "rating", "stars", "customer review", "verified purchase",
                "recommend", "pros and cons", "comparison", "benchmark", "unboxing"));

        return Collections.unmodifiableMap(topics);
    }
}
