package com.example.searchengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Paginated search response returned from GET /api/search.
 */
@Data
@Builder
public class SearchResponseDto {

    @JsonProperty("totalResults")
    private long totalResults;

    @JsonProperty("page")
    private int page;

    @JsonProperty("size")
    private int size;

    @JsonProperty("executionTimeMs")
    private long executionTimeMs;

    @JsonProperty("results")
    private List<SearchResultDto> results;
}
