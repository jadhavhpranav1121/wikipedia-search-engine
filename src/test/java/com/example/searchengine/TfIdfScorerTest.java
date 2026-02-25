package com.example.searchengine;

import com.example.searchengine.config.SearchProperties;
import com.example.searchengine.model.WikiDocument;
import com.example.searchengine.repository.InvertedIndexRepository;
import com.example.searchengine.service.TfIdfScorer;
import com.example.searchengine.util.StopWordFilter;
import com.example.searchengine.util.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TfIdfScorer")
class TfIdfScorerTest {

    @Mock private InvertedIndexRepository indexRepository;

    private TfIdfScorer scorer;
    private SearchProperties searchProperties;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        SearchProperties.RankingProperties rp = new SearchProperties.RankingProperties();
        rp.setTitleBoostFactor(2.5);
        searchProperties.setRanking(rp);

        scorer = new TfIdfScorer(indexRepository, searchProperties, new Tokenizer(new StopWordFilter()));
    }

    @Test
    @DisplayName("should return empty list for empty query tokens")
    void shouldReturnEmptyForEmptyQueryTokens() {
        assertThat(scorer.score(List.of())).isEmpty();
    }

    @Test
    @DisplayName("should return empty list when total document count is zero")
    void shouldReturnEmptyForEmptyIndex() {
        when(indexRepository.getTotalDocumentCount()).thenReturn(0L);
        assertThat(scorer.score(List.of("india"))).isEmpty();
    }

    @Test
    @DisplayName("should score documents and return sorted by score descending")
    void shouldScoreAndSortDescending() {
        when(indexRepository.getTotalDocumentCount()).thenReturn(100L);
        when(indexRepository.getDocumentFrequency("india")).thenReturn(10);

        // Doc 1: TF=5/100=0.05, Doc 2: TF=1/50=0.02
        when(indexRepository.getPostingList("india")).thenReturn(Map.of(1L, 5, 2L, 1));

        WikiDocument doc1 = WikiDocument.builder().id(1L).title("India").cleanText("india india india india india").tokenCount(100).build();
        WikiDocument doc2 = WikiDocument.builder().id(2L).title("History").cleanText("india").tokenCount(50).build();

        when(indexRepository.getDocument(1L)).thenReturn(Optional.of(doc1));
        when(indexRepository.getDocument(2L)).thenReturn(Optional.of(doc2));

        List<Map.Entry<Long, Double>> results = scorer.score(List.of("india"));

        assertThat(results).isNotEmpty();
        // Doc 1 has higher TF so should rank first
        assertThat(results.get(0).getKey()).isEqualTo(1L);
        assertThat(results.get(0).getValue()).isGreaterThan(results.get(1).getValue());
    }

    @Test
    @DisplayName("should apply AND semantics — exclude docs missing any token")
    void shouldApplyAndSemantics() {
        when(indexRepository.getTotalDocumentCount()).thenReturn(100L);
        when(indexRepository.getDocumentFrequency("india")).thenReturn(5);
        when(indexRepository.getDocumentFrequency("history")).thenReturn(3);

        // Doc 1 has both tokens, Doc 2 only has "india"
        when(indexRepository.getPostingList("india")).thenReturn(Map.of(1L, 2, 2L, 1));
        when(indexRepository.getPostingList("history")).thenReturn(Map.of(1L, 1));

        WikiDocument doc1 = WikiDocument.builder().id(1L).title("India History").cleanText("india history").tokenCount(10).build();
        when(indexRepository.getDocument(1L)).thenReturn(Optional.of(doc1));

        List<Map.Entry<Long, Double>> results = scorer.score(List.of("india", "history"));

        // Only doc 1 should appear (AND logic)
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getKey()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should return empty when no document satisfies AND query")
    void shouldReturnEmptyWhenNoDocSatisfiesAndQuery() {
        when(indexRepository.getTotalDocumentCount()).thenReturn(100L);
        when(indexRepository.getDocumentFrequency("india")).thenReturn(5);
        when(indexRepository.getDocumentFrequency("xyz123")).thenReturn(0);

        when(indexRepository.getPostingList("india")).thenReturn(Map.of(1L, 2));
        when(indexRepository.getPostingList("xyz123")).thenReturn(Map.of());

        List<Map.Entry<Long, Double>> results = scorer.score(List.of("india", "xyz123"));
        assertThat(results).isEmpty();
    }
}
