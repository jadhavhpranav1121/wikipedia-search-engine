package com.example.searchengine.service;

import com.example.searchengine.config.SearchProperties;
import com.example.searchengine.dto.SearchResponseDto;
import com.example.searchengine.dto.SearchResultDto;
import com.example.searchengine.exception.SearchException;
import com.example.searchengine.model.WikiDocument;
import com.example.searchengine.repository.InvertedIndexRepository;
import com.example.searchengine.util.SnippetGenerator;
import com.example.searchengine.util.Tokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.searchengine.config.CacheConfig.SEARCH_CACHE;

/**
 * Core search service.
 *
 * <p>Flow:
 * <ol>
 *   <li>Validate index is populated.</li>
 *   <li>Tokenize query.</li>
 *   <li>Run TF-IDF AND-query scoring.</li>
 *   <li>Paginate scored results.</li>
 *   <li>Fetch documents and generate snippets.</li>
 *   <li>Return paginated {@link SearchResponseDto}.</li>
 * </ol>
 *
 * <p>Results are cached by Caffeine keyed on {@code "q:page:size"}.
 * Cache is invalidated automatically after reindex.
 */
@Slf4j
@Service
public class SearchService {

    private final InvertedIndexRepository indexRepository;
    private final TfIdfScorer tfIdfScorer;
    private final Tokenizer tokenizer;
    private final SnippetGenerator snippetGenerator;
    private final SearchProperties searchProperties;

    public SearchService(InvertedIndexRepository indexRepository,
                         TfIdfScorer tfIdfScorer,
                         Tokenizer tokenizer,
                         SnippetGenerator snippetGenerator,
                         SearchProperties searchProperties) {
        this.indexRepository = indexRepository;
        this.tfIdfScorer = tfIdfScorer;
        this.tokenizer = tokenizer;
        this.snippetGenerator = snippetGenerator;
        this.searchProperties = searchProperties;
    }

    /**
     * Executes a paginated search query.
     * Results are Caffeine-cached with the composite key {@code q|page|size}.
     *
     * @param query raw user query string
     * @param page  zero-based page number
     * @param size  results per page
     * @return populated {@link SearchResponseDto}
     */
    @Cacheable(value = SEARCH_CACHE, key = "#query + '|' + #page + '|' + #size")
    public SearchResponseDto search(String query, int page, int size) {
        if (!indexRepository.isIndexPopulated()) {
            throw new SearchException("Index is empty. Please trigger a reindex via POST /api/admin/reindex.");
        }

        long startTime = System.currentTimeMillis();

        List<String> queryTokens = tokenizer.tokenizeQuery(query);
        if (queryTokens.isEmpty()) {
            log.warn("Query '{}' produced no tokens after processing.", query);
            return emptyResponse(query, page, size, System.currentTimeMillis() - startTime);
        }

        log.info("Executing query: '{}' | tokens: {} | page: {} | size: {}", query, queryTokens, page, size);

        // Score all matching documents
        List<Map.Entry<Long, Double>> scoredDocs = tfIdfScorer.score(queryTokens);
        long totalResults = scoredDocs.size();

        // Paginate
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, (int) totalResults);

        List<Map.Entry<Long, Double>> pageSlice = (fromIndex >= totalResults)
                ? List.of()
                : scoredDocs.subList(fromIndex, toIndex);

        // Build result DTOs
        int snippetLength = searchProperties.getSnippet().getLength();
        List<SearchResultDto> results = new ArrayList<>(pageSlice.size());

        for (Map.Entry<Long, Double> entry : pageSlice) {
            long docId = entry.getKey();
            double score = entry.getValue();

            Optional<WikiDocument> docOpt = indexRepository.getDocument(docId);
            if (docOpt.isEmpty()) {
                log.warn("Document {} in scores but not in store — skipping.", docId);
                continue;
            }

            WikiDocument doc = docOpt.get();
            String snippet = snippetGenerator.generate(doc.getCleanText(), queryTokens, snippetLength);

            results.add(SearchResultDto.builder()
                    .documentId(docId)
                    .title(doc.getTitle())
                    .score(Math.round(score * 100.0) / 100.0)
                    .snippet(snippet)
                    .build());
        }

        long execTimeMs = System.currentTimeMillis() - startTime;
        log.info("Query '{}' completed in {} ms — {} total results", query, execTimeMs, totalResults);

        return SearchResponseDto.builder()
                .totalResults(totalResults)
                .page(page)
                .size(size)
                .executionTimeMs(execTimeMs)
                .results(results)
                .build();
    }

    private SearchResponseDto emptyResponse(String query, int page, int size, long execMs) {
        return SearchResponseDto.builder()
                .totalResults(0)
                .page(page)
                .size(size)
                .executionTimeMs(execMs)
                .results(List.of())
                .build();
    }
}
