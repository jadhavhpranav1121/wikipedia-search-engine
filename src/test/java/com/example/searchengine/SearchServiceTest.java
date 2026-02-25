package com.example.searchengine;

import com.example.searchengine.config.SearchProperties;
import com.example.searchengine.dto.SearchResponseDto;
import com.example.searchengine.exception.SearchException;
import com.example.searchengine.model.WikiDocument;
import com.example.searchengine.repository.InvertedIndexRepository;
import com.example.searchengine.service.SearchService;
import com.example.searchengine.service.TfIdfScorer;
import com.example.searchengine.util.SnippetGenerator;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchService")
class SearchServiceTest {

    @Mock private InvertedIndexRepository indexRepository;
    @Mock private TfIdfScorer tfIdfScorer;
    @Mock private SnippetGenerator snippetGenerator;

    private SearchService searchService;
    private SearchProperties searchProperties;
    private Tokenizer tokenizer;

    @BeforeEach
    void setUp() {
        searchProperties = new SearchProperties();
        SearchProperties.SnippetProperties snippetProps = new SearchProperties.SnippetProperties();
        snippetProps.setLength(300);
        searchProperties.setSnippet(snippetProps);

        SearchProperties.RankingProperties rankingProps = new SearchProperties.RankingProperties();
        rankingProps.setTitleBoostFactor(2.5);
        searchProperties.setRanking(rankingProps);

        tokenizer = new Tokenizer(new StopWordFilter());

        searchService = new SearchService(
                indexRepository, tfIdfScorer, tokenizer, snippetGenerator, searchProperties);
    }

    @Test
    @DisplayName("should throw SearchException when index is empty")
    void shouldThrowWhenIndexIsEmpty() {
        when(indexRepository.isIndexPopulated()).thenReturn(false);

        assertThatThrownBy(() -> searchService.search("india", 0, 10))
                .isInstanceOf(SearchException.class)
                .hasMessageContaining("Index is empty");
    }

    @Test
    @DisplayName("should return empty response for zero-token query")
    void shouldReturnEmptyResponseForZeroTokenQuery() {
        when(indexRepository.isIndexPopulated()).thenReturn(true);

        // A query that tokenizes to nothing (e.g., all stop words or punctuation)
        SearchResponseDto response = searchService.search("a", 0, 10);
        assertThat(response.getTotalResults()).isEqualTo(0);
        assertThat(response.getResults()).isEmpty();
    }

    @Test
    @DisplayName("should return paginated results for valid query")
    void shouldReturnPaginatedResults() {
        when(indexRepository.isIndexPopulated()).thenReturn(true);

        WikiDocument doc = WikiDocument.builder()
                .id(1L).title("History of India")
                .cleanText("India is a country in South Asia with rich history.")
                .tokenCount(10).build();

        when(tfIdfScorer.score(anyList())).thenReturn(
                List.of(Map.entry(1L, 8.91)));
        when(indexRepository.getDocument(1L)).thenReturn(Optional.of(doc));
        when(snippetGenerator.generate(anyString(), anyList(), anyInt()))
                .thenReturn("India is a country in South Asia...");

        SearchResponseDto response = searchService.search("india history", 0, 10);

        assertThat(response.getTotalResults()).isEqualTo(1L);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getTitle()).isEqualTo("History of India");
        assertThat(response.getResults().get(0).getScore()).isEqualTo(8.91);
    }

    @Test
    @DisplayName("should return correct page slice")
    void shouldReturnCorrectPageSlice() {
        when(indexRepository.isIndexPopulated()).thenReturn(true);

        // Simulate 5 scored docs, request page 1 size 2
        List<Map.Entry<Long, Double>> scored = List.of(
                Map.entry(1L, 9.0), Map.entry(2L, 8.0),
                Map.entry(3L, 7.0), Map.entry(4L, 6.0), Map.entry(5L, 5.0));

        when(tfIdfScorer.score(anyList())).thenReturn(scored);

        WikiDocument doc3 = WikiDocument.builder().id(3L).title("Doc 3").cleanText("text").tokenCount(1).build();
        WikiDocument doc4 = WikiDocument.builder().id(4L).title("Doc 4").cleanText("text").tokenCount(1).build();

        when(indexRepository.getDocument(3L)).thenReturn(Optional.of(doc3));
        when(indexRepository.getDocument(4L)).thenReturn(Optional.of(doc4));
        when(snippetGenerator.generate(anyString(), anyList(), anyInt())).thenReturn("...");

        SearchResponseDto response = searchService.search("india", 1, 2);

        assertThat(response.getTotalResults()).isEqualTo(5L);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().get(0).getDocumentId()).isEqualTo(3L);
        assertThat(response.getResults().get(1).getDocumentId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("should return empty page when page index is out of range")
    void shouldReturnEmptyPageOutOfRange() {
        when(indexRepository.isIndexPopulated()).thenReturn(true);
        when(tfIdfScorer.score(anyList())).thenReturn(List.of(Map.entry(1L, 5.0)));

        SearchResponseDto response = searchService.search("india", 99, 10);
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalResults()).isEqualTo(1L);
    }
}
