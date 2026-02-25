package com.example.searchengine;

import com.example.searchengine.util.StopWordFilter;
import com.example.searchengine.util.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tokenizer")
class TokenizerTest {

    private Tokenizer tokenizer;

    @BeforeEach
    void setUp() {
        tokenizer = new Tokenizer(new StopWordFilter());
    }

    @Test
    @DisplayName("should tokenize simple text")
    void shouldTokenizeSimpleText() {
        List<String> tokens = tokenizer.tokenize("India is a country in South Asia");
        assertThat(tokens).contains("india", "country", "south", "asia");
    }

    @Test
    @DisplayName("should remove stop words")
    void shouldRemoveStopWords() {
        List<String> tokens = tokenizer.tokenize("India is a country");
        assertThat(tokens).doesNotContain("is", "a");
        assertThat(tokens).contains("india", "country");
    }

    @Test
    @DisplayName("should lowercase all tokens")
    void shouldLowercaseTokens() {
        List<String> tokens = tokenizer.tokenize("INDIA History CULTURE");
        assertThat(tokens).contains("india", "history", "culture");
        assertThat(tokens).doesNotContain("INDIA", "History");
    }

    @Test
    @DisplayName("should remove punctuation")
    void shouldRemovePunctuation() {
        List<String> tokens = tokenizer.tokenize("Hello, world! How are you?");
        assertThat(tokens).contains("hello", "world");
        assertThat(tokens).doesNotContain(",", "!", "?");
    }

    @Test
    @DisplayName("should return empty list for null input")
    void shouldReturnEmptyForNull() {
        assertThat(tokenizer.tokenize(null)).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for blank input")
    void shouldReturnEmptyForBlank() {
        assertThat(tokenizer.tokenize("   ")).isEmpty();
    }

    @Test
    @DisplayName("tokenizeQuery should not filter stop words")
    void shouldNotFilterStopWordsInQuery() {
        // Query tokenization preserves more tokens for user intent
        List<String> tokens = tokenizer.tokenizeQuery("history of india");
        // "of" is only 2 chars, passes length check
        assertThat(tokens).contains("history", "india");
    }

    @Test
    @DisplayName("should filter single-character tokens")
    void shouldFilterSingleCharTokens() {
        List<String> tokens = tokenizer.tokenize("a b c india");
        assertThat(tokens).contains("india");
        assertThat(tokens).doesNotContain("a", "b", "c");
    }
}
