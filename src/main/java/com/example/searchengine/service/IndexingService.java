package com.example.searchengine.service;

import com.example.searchengine.config.SearchProperties;
import com.example.searchengine.indexing.IndexBuilder;
import com.example.searchengine.indexing.WikiDumpParser;
import com.example.searchengine.model.IndexingStatus;
import com.example.searchengine.model.WikiDocument;
import com.example.searchengine.repository.InvertedIndexRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates the full Wikipedia indexing pipeline:
 * BZ2 streaming → StAX parsing → batch tokenization → index construction → atomic swap.
 *
 * <p>The reindex method is marked {@code @Async} so the HTTP response to
 * POST /api/admin/reindex returns immediately while indexing runs in the background.
 * A concurrent flag prevents overlapping reindex operations.
 */
@Slf4j
@Service
public class IndexingService {

    private final WikiDumpParser dumpParser;
    private final IndexBuilder indexBuilder;
    private final InvertedIndexRepository indexRepository;
    private final SearchProperties searchProperties;
    private final CacheManager cacheManager;

    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private final AtomicReference<IndexingStatus> lastStatus =
            new AtomicReference<>(IndexingStatus.builder()
                    .state(IndexingStatus.State.IDLE)
                    .build());

    public IndexingService(WikiDumpParser dumpParser,
                           IndexBuilder indexBuilder,
                           InvertedIndexRepository indexRepository,
                           SearchProperties searchProperties,
                           CacheManager cacheManager) {
        this.dumpParser = dumpParser;
        this.indexBuilder = indexBuilder;
        this.indexRepository = indexRepository;
        this.searchProperties = searchProperties;
        this.cacheManager = cacheManager;
    }

    /**
     * Asynchronously reindexes the Wikipedia dump file.
     * If indexing is already running, throws {@link IllegalStateException}.
     */
    @Async("taskExecutor")
    public void reindex() {
        if (!indexingInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("Reindexing is already in progress. Please wait.");
        }

        Instant startedAt = Instant.now();
        lastStatus.set(IndexingStatus.builder()
                .state(IndexingStatus.State.RUNNING)
                .startedAt(startedAt)
                .build());

        log.info("=== Reindex started at {} ===", startedAt);
        logMemoryUsage("Before indexing");

        // Fresh index structures (written to by batch workers)
        ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> newInvertedIndex =
                new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, WikiDocument> newDocumentStore = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Integer> newDocFrequency = new ConcurrentHashMap<>();
        AtomicLong processedCount = new AtomicLong(0);

        int batchSize = searchProperties.getIndexing().getBatchSize();
        int maxDocs = searchProperties.getIndexing().getMaxDocuments();
        String dumpPath = searchProperties.getDumpPath();

        try {
            List<WikiDocument> currentBatch = new ArrayList<>(batchSize);

            dumpParser.parseWithStateMachine(dumpPath, maxDocs, doc -> {
                currentBatch.add(doc);
                if (currentBatch.size() >= batchSize) {
                    List<WikiDocument> batchToProcess = new ArrayList<>(currentBatch);
                    currentBatch.clear();
                    indexBuilder.processBatch(batchToProcess, newInvertedIndex,
                            newDocumentStore, newDocFrequency, processedCount);
                }
            });

            // Process the last partial batch
            if (!currentBatch.isEmpty()) {
                indexBuilder.processBatch(currentBatch, newInvertedIndex,
                        newDocumentStore, newDocFrequency, processedCount);
            }

            // Atomically replace the live index
            indexRepository.replaceIndex(newInvertedIndex, newDocumentStore, newDocFrequency);

            // Invalidate all search caches after reindex
            invalidateSearchCache();

            long durationMs = Instant.now().toEpochMilli() - startedAt.toEpochMilli();
            long memMb = getUsedMemoryMb();

            log.info("=== Reindex complete ===");
            log.info("Documents processed : {}", processedCount.get());
            log.info("Unique tokens       : {}", newInvertedIndex.size());
            log.info("Duration            : {} ms", durationMs);
            log.info("Memory used         : {} MB", memMb);
            logMemoryUsage("After indexing");

            lastStatus.set(IndexingStatus.builder()
                    .state(IndexingStatus.State.COMPLETED)
                    .documentsProcessed(processedCount.get())
                    .durationMs(durationMs)
                    .memoryUsedMb(memMb)
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .build());

        } catch (Exception e) {
            log.error("Reindexing failed: {}", e.getMessage(), e);
            lastStatus.set(IndexingStatus.builder()
                    .state(IndexingStatus.State.FAILED)
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .errorMessage(e.getMessage())
                    .documentsProcessed(processedCount.get())
                    .build());
        } finally {
            indexingInProgress.set(false);
        }
    }

    /**
     * Returns the status of the most recent (or current) indexing operation.
     */
    public IndexingStatus getLastIndexingStatus() {
        return lastStatus.get();
    }

    /**
     * Returns true if indexing is currently in progress.
     */
    public boolean isIndexingInProgress() {
        return indexingInProgress.get();
    }

    private void invalidateSearchCache() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
                log.info("Cache '{}' cleared after reindex.", name);
            }
        });
    }

    private void logMemoryUsage(String label) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long totalMb = rt.totalMemory() / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        log.info("[Memory - {}] Used: {} MB / Total: {} MB / Max: {} MB", label, usedMb, totalMb, maxMb);
    }

    private long getUsedMemoryMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }
}
