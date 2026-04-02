package com.shriram.crawler.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageMetadata {

    private String url;
    private String title;
    private String description;
    private String bodyText;
    private List<String> topics;
    private boolean success;
    private String errorMessage;
}
