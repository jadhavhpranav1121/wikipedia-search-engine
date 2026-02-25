package com.example.searchengine.indexing;

import com.example.searchengine.model.WikiDocument;
import com.example.searchengine.util.Tokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds the inverted index from a stream of WikiDocuments.
 *
 * <p>Processing strategy:
 * <ol>
 *   <li>Documents are collected into batches (configurable size).</li>
 *   <li>Each batch is submitted to an ExecutorService for parallel tokenization.</li>
 *   <li>Each worker thread writes into thread-safe ConcurrentHashMaps with
 *       atomic merge operations.</li>
 *   <li>At the end, a single consolidated index is returned for atomic swap
 *       into the repository.</li>
 * </ol>
 */
@Slf4j
@Component
public class IndexBuilder {

    private final Tokenizer tokenizer;
    private final ExecutorService executorService;

    public IndexBuilder(Tokenizer tokenizer,
                        ExecutorService indexingExecutorService) {
        this.tokenizer = tokenizer;
        this.executorService = indexingExecutorService;
    }

    /**
     * Result container holding both index structures post-build.
     */
    public record IndexBuildResult(
            ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> invertedIndex,
            ConcurrentHashMap<Long, WikiDocument> documentStore,
            ConcurrentHashMap<String, Integer> documentFrequency
    ) {}

    /**
     * Processes a batch of WikiDocuments and merges the results into the shared
     * index structures.
     *
     * @param documents       list of documents in this batch
     * @param invertedIndex   shared inverted index (written to atomically)
     * @param documentStore   shared document store
     * @param documentFrequency shared DF map
     * @param processedCount  atomic counter for progress tracking
     */
    public void processBatch(
            List<WikiDocument> documents,
            ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> invertedIndex,
            ConcurrentHashMap<Long, WikiDocument> documentStore,
            ConcurrentHashMap<String, Integer> documentFrequency,
            AtomicLong processedCount) {

        List<Future<?>> futures = new ArrayList<>(documents.size());

        for (WikiDocument doc : documents) {
            futures.add(executorService.submit(() -> indexDocument(
                    doc, invertedIndex, documentStore, documentFrequency)));
        }

        // Wait for all batch futures
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                log.warn("Error indexing document in batch: {}", e.getMessage());
            }
        }

        long total = processedCount.addAndGet(documents.size());
        log.debug("Batch processed. Running total: {}", total);
    }

    /**
     * Indexes a single document: tokenizes its text, updates the inverted index,
     * document store, and document frequency map.
     */
    private void indexDocument(
            WikiDocument doc,
            ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> invertedIndex,
            ConcurrentHashMap<Long, WikiDocument> documentStore,
            ConcurrentHashMap<String, Integer> documentFrequency) {

        List<String> tokens = tokenizer.tokenize(doc.getCleanText());
        if (tokens.isEmpty()) {
            return;
        }

        doc.setTokenCount(tokens.size());
        documentStore.put(doc.getId(), doc);

        // Count term frequency for this document
        Map<String, Integer> termFreq = new ConcurrentHashMap<>();
        for (String token : tokens) {
            termFreq.merge(token, 1, Integer::sum);
        }

        // Write into inverted index
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String token = entry.getKey();
            int tf = entry.getValue();

            invertedIndex
                    .computeIfAbsent(token, k -> new ConcurrentHashMap<>())
                    .put(doc.getId(), tf);

            // Increment document frequency atomically
            documentFrequency.merge(token, 1, Integer::sum);
        }
    }
}
