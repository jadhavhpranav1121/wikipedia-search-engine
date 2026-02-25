package com.example.searchengine.controller;

import com.example.searchengine.dto.SearchResponseDto;
import com.example.searchengine.service.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for search queries.
 *
 * <p>All business logic is delegated to {@link SearchService}.
 * This controller is responsible only for HTTP concerns:
 * input validation, response mapping, and HTTP status codes.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * GET /api/search?q={query}&page={page}&size={size}
     *
     * <p>Executes a TF-IDF ranked search query with AND semantics.
     *
     * @param query the search query (1-200 chars)
     * @param page  zero-based page number (default 0)
     * @param size  results per page (1-50, default 10)
     * @return paginated search response
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponseDto> search(
            @RequestParam("q")
            @NotBlank(message = "Query parameter 'q' must not be blank")
            @Size(min = 1, max = 200, message = "Query must be between 1 and 200 characters")
            String query,

            @RequestParam(value = "page", defaultValue = "0")
            @Min(value = 0, message = "Page must be >= 0")
            int page,

            @RequestParam(value = "size", defaultValue = "10")
            @Min(value = 1, message = "Size must be >= 1")
            @Max(value = 50, message = "Size must be <= 50")
            int size) {

        log.debug("Search request: q='{}', page={}, size={}", query, page, size);
        SearchResponseDto response = searchService.search(query.trim(), page, size);
        return ResponseEntity.ok(response);
    }
}
