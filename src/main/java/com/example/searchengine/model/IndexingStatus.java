package com.example.searchengine.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Represents the current status of an indexing operation.
 */
@Data
@Builder
public class IndexingStatus {

    public enum State { IDLE, RUNNING, COMPLETED, FAILED }

    private State state;
    private long documentsProcessed;
    private long durationMs;
    private long memoryUsedMb;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
}
