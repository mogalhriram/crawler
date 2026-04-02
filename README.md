# Web Crawler API

A Spring Boot REST API that crawls web pages, extracts HTML metadata, and classifies pages into relevant topics.

## Tech Stack

- Java 21
- Spring Boot 4.0.5
- Jsoup (HTML parsing)
- Virtual Threads (parallel crawling)

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
- **In-memory caching** with 5-minute TTL and 500-entry limit
- **URL deduplication** within the same request
- **Bot detection bypass** with browser-like HTTP headers
- **Resilient** — handles HTTP errors, invalid SSL, non-HTML content, malformed URLs

## Running Locally

```bash
./mvnw spring-boot:run
```

The server starts on port `5000`.

## Running on AWS (Free Tier)

```bash
./mvnw clean package -DskipTests
java -Xms256m -Xmx512m -jar target/crawler-0.0.1-SNAPSHOT.jar
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
