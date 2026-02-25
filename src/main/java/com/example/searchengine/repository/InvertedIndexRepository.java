package com.example.searchengine.repository;

import com.example.searchengine.model.WikiDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory repository for the inverted index and document store.
 *
 * <p>Data structures:
 * <ul>
 *   <li>{@code invertedIndex}: token → (docId → termFrequency)</li>
 *   <li>{@code documentStore}: docId → WikiDocument</li>
 *   <li>{@code documentFrequency}: token → number of documents containing the token (for IDF)</li>
 * </ul>
 *
 * <p>Write operations are synchronized at the document level via ConcurrentHashMap.
 * Clearing the index and rebuilding it atomically uses synchronized blocks.
 */
@Slf4j
@Repository
public class InvertedIndexRepository {

    // token -> (docId -> termFrequency)
    private volatile ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> invertedIndex
            = new ConcurrentHashMap<>();

    // docId -> WikiDocument
    private volatile ConcurrentHashMap<Long, WikiDocument> documentStore
            = new ConcurrentHashMap<>();

    // token -> document frequency count (# docs containing token)
    private volatile ConcurrentHashMap<String, Integer> documentFrequency
            = new ConcurrentHashMap<>();

    private final AtomicLong totalDocumentCount = new AtomicLong(0);

    // -----------------------------------------------------------------------
    // Write operations
    // -----------------------------------------------------------------------

    /**
     * Atomically replaces the entire index with a new set of pre-built structures.
     * Called at the end of a full reindex cycle.
     *
     * @param newIndex     new inverted index
     * @param newDocStore  new document store
     * @param newDocFreq   new document frequency map
     */
    public synchronized void replaceIndex(
            ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> newIndex,
            ConcurrentHashMap<Long, WikiDocument> newDocStore,
            ConcurrentHashMap<String, Integer> newDocFreq) {
        this.invertedIndex = newIndex;
        this.documentStore = newDocStore;
        this.documentFrequency = newDocFreq;
        this.totalDocumentCount.set(newDocStore.size());
        log.info("Index replaced: {} documents, {} unique tokens",
                newDocStore.size(), newIndex.size());
    }

    // -----------------------------------------------------------------------
    // Read operations
    // -----------------------------------------------------------------------

    /**
     * Returns the posting list for a given token.
     *
     * @param token lowercase token
     * @return map of docId → termFrequency, or empty map if token not indexed
     */
    public Map<Long, Integer> getPostingList(String token) {
        ConcurrentHashMap<Long, Integer> postings = invertedIndex.get(token);
        return postings != null ? Collections.unmodifiableMap(postings) : Collections.emptyMap();
    }

    /**
     * Returns the document frequency of a token (how many docs contain it).
     *
     * @param token lowercase token
     * @return document frequency count
     */
    public int getDocumentFrequency(String token) {
        return documentFrequency.getOrDefault(token, 0);
    }

    /**
     * Returns a WikiDocument by its ID.
     *
     * @param docId document ID
     * @return optional WikiDocument
     */
    public Optional<WikiDocument> getDocument(long docId) {
        return Optional.ofNullable(documentStore.get(docId));
    }

    /**
     * Returns the total number of indexed documents. Used for IDF calculation.
     *
     * @return total document count
     */
    public long getTotalDocumentCount() {
        return totalDocumentCount.get();
    }

    /**
     * Returns the complete set of indexed tokens. Primarily for diagnostics.
     *
     * @return set of all tokens
     */
    public Set<String> getAllTokens() {
        return Collections.unmodifiableSet(invertedIndex.keySet());
    }

    /**
     * Returns true if the index contains at least one document.
     *
     * @return true if index is populated
     */
    public boolean isIndexPopulated() {
        return !documentStore.isEmpty();
    }
}
