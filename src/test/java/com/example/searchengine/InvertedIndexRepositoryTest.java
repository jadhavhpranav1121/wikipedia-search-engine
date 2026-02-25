package com.example.searchengine;

import com.example.searchengine.model.WikiDocument;
import com.example.searchengine.repository.InvertedIndexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvertedIndexRepository")
class InvertedIndexRepositoryTest {

    private InvertedIndexRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InvertedIndexRepository();
    }

    @Test
    @DisplayName("should return empty map for unknown token")
    void shouldReturnEmptyPostingForUnknownToken() {
        assertThat(repository.getPostingList("unknowntoken")).isEmpty();
    }

    @Test
    @DisplayName("should return 0 document count for empty index")
    void shouldReturnZeroDocumentCountForEmpty() {
        assertThat(repository.getTotalDocumentCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("should correctly replace index and allow reads")
    void shouldReplaceIndexAndAllowReads() {
        ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> index = new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, Integer> postings = new ConcurrentHashMap<>();
        postings.put(42L, 3);
        index.put("india", postings);

        ConcurrentHashMap<Long, WikiDocument> docStore = new ConcurrentHashMap<>();
        WikiDocument doc = WikiDocument.builder()
                .id(42L).title("India").cleanText("India is a country.").tokenCount(4).build();
        docStore.put(42L, doc);

        ConcurrentHashMap<String, Integer> docFreq = new ConcurrentHashMap<>();
        docFreq.put("india", 1);

        repository.replaceIndex(index, docStore, docFreq);

        assertThat(repository.getTotalDocumentCount()).isEqualTo(1L);
        assertThat(repository.getPostingList("india")).containsEntry(42L, 3);
        assertThat(repository.getDocumentFrequency("india")).isEqualTo(1);

        Optional<WikiDocument> retrieved = repository.getDocument(42L);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getTitle()).isEqualTo("India");
    }

    @Test
    @DisplayName("should return empty Optional for missing document")
    void shouldReturnEmptyOptionalForMissingDoc() {
        assertThat(repository.getDocument(9999L)).isEmpty();
    }

    @Test
    @DisplayName("isIndexPopulated should return false for empty index")
    void shouldReturnFalseForEmptyIndex() {
        assertThat(repository.isIndexPopulated()).isFalse();
    }

    @Test
    @DisplayName("isIndexPopulated should return true after replaceIndex")
    void shouldReturnTrueAfterReplaceIndex() {
        ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> index = new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, WikiDocument> docStore = new ConcurrentHashMap<>();
        docStore.put(1L, WikiDocument.builder().id(1L).title("Test").cleanText("text").build());

        repository.replaceIndex(index, docStore, new ConcurrentHashMap<>());
        assertThat(repository.isIndexPopulated()).isTrue();
    }

    @Test
    @DisplayName("should return 0 document frequency for unknown token")
    void shouldReturnZeroDocFreqForUnknownToken() {
        assertThat(repository.getDocumentFrequency("nonexistent")).isEqualTo(0);
    }
}
