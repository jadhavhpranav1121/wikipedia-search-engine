package com.example.searchengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * A single search result returned to the client.
 */
@Data
@Builder
public class SearchResultDto {

    @JsonProperty("documentId")
    private long documentId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("score")
    private double score;

    @JsonProperty("snippet")
    private String snippet;
}
