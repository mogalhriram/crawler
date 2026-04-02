package com.shriram.crawler.controller;

import com.shriram.crawler.dto.CrawlRequest;
import com.shriram.crawler.dto.CrawlResponse;
import com.shriram.crawler.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crawl")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlerService crawlerService;

    @PostMapping
    public ResponseEntity<CrawlResponse> crawl(@RequestBody CrawlRequest request) {
        if (request.getUrls() == null || request.getUrls().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        CrawlResponse response = crawlerService.crawl(request);
        return ResponseEntity.ok(response);
    }
}
