package com.example.searchengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Response returned from POST /api/admin/reindex.
 */
@Data
@Builder
public class ReindexResponseDto {

    @JsonProperty("message")
    private String message;

    @JsonProperty("status")
    private String status;
}
