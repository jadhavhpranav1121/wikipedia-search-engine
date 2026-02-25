package com.example.searchengine.controller;

import com.example.searchengine.dto.ReindexResponseDto;
import com.example.searchengine.model.IndexingStatus;
import com.example.searchengine.service.IndexingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for index management operations.
 *
 * <p>These endpoints are intended for operators/admins only.
 * In a production deployment, protect these with Spring Security or an API gateway.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IndexingService indexingService;

    public AdminController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    /**
     * POST /api/admin/reindex
     *
     * <p>Triggers an asynchronous full reindex of the Wikipedia dump file.
     * Returns immediately with HTTP 202 Accepted. Monitor progress via GET /api/admin/status.
     *
     * @return 202 Accepted with message, or 409 Conflict if indexing is already running
     */
    @PostMapping("/reindex")
    public ResponseEntity<ReindexResponseDto> reindex() {
        log.info("Reindex triggered via POST /api/admin/reindex");

        if (indexingService.isIndexingInProgress()) {
            return ResponseEntity.status(409).body(
                    ReindexResponseDto.builder()
                            .status("CONFLICT")
                            .message("Reindexing is already in progress. Please wait for it to complete.")
                            .build()
            );
        }

        // Fire-and-forget (async)
        indexingService.reindex();

        return ResponseEntity.accepted().body(
                ReindexResponseDto.builder()
                        .status("ACCEPTED")
                        .message("Reindexing started asynchronously. Check GET /api/admin/status for progress.")
                        .build()
        );
    }

    /**
     * GET /api/admin/status
     *
     * <p>Returns the status of the most recent (or current) indexing operation.
     *
     * @return current indexing status
     */
    @GetMapping("/status")
    public ResponseEntity<IndexingStatus> getStatus() {
        return ResponseEntity.ok(indexingService.getLastIndexingStatus());
    }
}
