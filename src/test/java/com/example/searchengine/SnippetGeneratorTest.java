package com.example.searchengine;

import com.example.searchengine.util.SnippetGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SnippetGenerator")
class SnippetGeneratorTest {

    private SnippetGenerator snippetGenerator;

    @BeforeEach
    void setUp() {
        snippetGenerator = new SnippetGenerator();
    }

    @Test
    @DisplayName("should generate snippet centered around query term")
    void shouldGenerateSnippetAroundQueryTerm() {
        String text = "Lorem ipsum dolor sit amet. India is a country located in South Asia. It has a rich history.";
        String snippet = snippetGenerator.generate(text, List.of("india"), 60);
        assertThat(snippet).containsIgnoringCase("india");
    }

    @Test
    @DisplayName("should return beginning of text when no query term found")
    void shouldReturnBeginningWhenNoMatch() {
        String text = "This is some text about something completely different.";
        String snippet = snippetGenerator.generate(text, List.of("xyz999"), 30);
        assertThat(snippet).isNotBlank();
        assertThat(snippet.length()).isLessThanOrEqualTo(35); // snippet length + possible "..."
    }

    @Test
    @DisplayName("should return empty string for null text")
    void shouldReturnEmptyForNull() {
        assertThat(snippetGenerator.generate(null, List.of("india"), 300)).isEmpty();
    }

    @Test
    @DisplayName("should return empty string for blank text")
    void shouldReturnEmptyForBlank() {
        assertThat(snippetGenerator.generate("   ", List.of("india"), 300)).isEmpty();
    }

    @Test
    @DisplayName("should add trailing ellipsis when text is truncated")
    void shouldAddTrailingEllipsis() {
        String longText = "India is a country. ".repeat(50);
        String snippet = snippetGenerator.generate(longText, List.of("india"), 50);
        assertThat(snippet).endsWith("...");
    }

    @Test
    @DisplayName("should not exceed specified snippet length significantly")
    void shouldRespectSnippetLength() {
        String text = "India is a country in South Asia with a very long description that goes on and on.";
        String snippet = snippetGenerator.generate(text, List.of("india"), 30);
        // Allow some flex for ellipsis and word boundary alignment
        assertThat(snippet.length()).isLessThan(60);
    }
}
