package com.shriram.crawler.dto;

import lombok.Data;

import java.util.List;

@Data
public class CrawlRequest {

    private List<String> urls;
}
