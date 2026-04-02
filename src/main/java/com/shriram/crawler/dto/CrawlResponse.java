package com.shriram.crawler.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CrawlResponse {

    private int totalRequested;
    private int totalSuccessful;
    private long timeTakenMs;
    private List<PageMetadata> results;
}
