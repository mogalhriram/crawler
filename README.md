# Web Crawler API

A Spring Boot REST API that crawls web pages, extracts HTML metadata, and classifies pages into relevant topics.

## Tech Stack

- Java 21
- Spring Boot 4.0.5
- [Jsoup](https://jsoup.org/) — HTTP client + HTML parser (see below)
- Virtual Threads (parallel crawling)

## Crawling library: Jsoup

HTTP requests and HTML parsing use **[Jsoup](https://jsoup.org/)** — Maven coordinates `org.jsoup:jsoup` (**1.18.3** in this project; see `pom.xml`). Source: [jhy/jsoup](https://github.com/jhy/jsoup).

| Role | How it is used |
|------|----------------|
| **Fetch** | `Jsoup.connect(url)` performs the GET (with redirects, timeout, headers, and body size limit configured in code). |
| **Parse** | `Connection.Response` → `Document` for CSS-selector queries (`title`, `meta`, `body.text()`, etc.). |
| **Not included** | Jsoup does **not** execute JavaScript, render pages, or drive a browser — it only sees the HTML returned in that single response. |

For heavier sites that need a real browser, you would add something like Playwright or Selenium alongside or instead of Jsoup.

## API

### POST `/api/v1/crawl`

**Request:**

```json
{
  "urls": [
    "http://www.amazon.com/Cuisinart-CPT-122-Compact-2-Slice-Toaster/dp/B009GQ034C",
    "https://www.google.com"
  ]
}
```

**Response:**

```json
{
  "totalRequested": 2,
  "totalSuccessful": 2,
  "timeTakenMs": 1523,
  "results": [
    {
      "url": "http://www.amazon.com/...",
      "title": "Cuisinart CPT-122 2-Slice Compact Plastic Toaster",
      "description": "Online Shopping for Kitchen Small Appliances...",
      "bodyText": "First 5000 characters of page body...",
      "topics": ["Kitchen & Home Appliances", "E-Commerce", "Product Reviews"],
      "success": true,
      "errorMessage": null
    }
  ]
}
```

## Features

- **Parallel crawling** using Java 21 virtual threads
- **Topic classification** via keyword frequency analysis (14 categories)
- **Bot detection bypass** with browser-like HTTP headers
- **Resilient** — handles HTTP errors, invalid SSL, non-HTML content, malformed URLs

## Limitations

- **No JavaScript** — Parsing is done with Jsoup on the raw HTML only. Pages that render most content in the browser (React, Vue, many news sites) may return an **empty or very short `bodyText`** while `title` / `description` can still be filled from `<head>` meta tags.
- **`bodyText` source** — Taken from `document.body` visible text. It can include nav, footers, and ads on traditional sites; it does not follow **iframes**, **Shadow DOM**, or JSON-in-`<script>` article payloads.
- **Truncation** — `bodyText` is capped at **5000 characters** in the API response.
- **Topics** — Heuristic keyword scoring over a fixed dictionary, not machine learning; results are indicative only.
- **Remote behavior** — Sites may rate-limit, block datacenter IPs, or require cookies; some responses are paywalls or bot challenges rather than full articles.
- **Single URL** — Only the requested URL is fetched; multi-page articles are not followed automatically.

## Running Locally

### IntelliJ (recommended)

1. **File → Project Structure → Project**: SDK **21**.
2. Open the **Maven** tool window (**View → Tool Windows → Maven**).
3. Expand **crawler → Lifecycle**.
4. Double-click **`clean`**, then **`package`** (add **`-DskipTests`** in the run configuration or Maven runner if tests fail).
5. Runnable JAR: **`target/crawler.jar`** (set by `finalName` in `pom.xml`).

Run the app: **Run → Run 'CrawlerApplication'** or:

```bash
java -jar target/crawler.jar
```

### Command line

```bash
./mvnw spring-boot:run
```

The server starts on port `5000`.

## Running on AWS (Free Tier)

```bash
./mvnw clean package -DskipTests
java -Xms256m -Xmx512m -jar target/crawler.jar
```

## Testing with cURL

```bash
curl -X POST http://localhost:5000/api/v1/crawl \
  -H "Content-Type: application/json" \
  -d '{
    "urls": [
      "http://www.amazon.com/Cuisinart-CPT-122-Compact-2-Slice-Toaster/dp/B009GQ034C/ref=sr_1_1?s=kitchen&ie=UTF8&qid=1431620315&sr=1-1&keywords=toaster"
    ]
  }'
```

## Project Structure

```
src/main/java/com/shriram/crawler/
├── CrawlerApplication.java        # Entry point + ExecutorService bean
├── controller/
│   └── CrawlerController.java     # REST endpoint
├── dto/
│   ├── CrawlRequest.java          # Input: list of URLs
│   ├── CrawlResponse.java         # Output: results + timing
│   └── PageMetadata.java          # Per-URL extracted metadata
└── service/
    └── CrawlerService.java        # Core crawling, extraction, classification
```
