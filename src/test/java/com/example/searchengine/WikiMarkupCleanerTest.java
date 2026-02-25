package com.example.searchengine;

import com.example.searchengine.util.WikiMarkupCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WikiMarkupCleaner")
class WikiMarkupCleanerTest {

    private WikiMarkupCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new WikiMarkupCleaner();
    }

    @Test
    @DisplayName("should remove wiki link with display text")
    void shouldRemoveWikiLinkDisplayText() {
        String input = "This is a [[India|Republic of India]] article.";
        String cleaned = cleaner.clean(input);
        assertThat(cleaned).contains("Republic of India");
        assertThat(cleaned).doesNotContain("[[");
    }

    @Test
    @DisplayName("should remove plain wiki links")
    void shouldRemovePlainWikiLinks() {
        String input = "Refer to [[History]] for more details.";
        String cleaned = cleaner.clean(input);
        assertThat(cleaned).contains("History");
        assertThat(cleaned).doesNotContain("[[");
    }

    @Test
    @DisplayName("should remove templates")
    void shouldRemoveTemplates() {
        String input = "{{Infobox country|name=India}} India is a country.";
        String cleaned = cleaner.clean(input);
        assertThat(cleaned).contains("India is a country");
        assertThat(cleaned).doesNotContain("{{");
    }

    @Test
    @DisplayName("should remove HTML tags")
    void shouldRemoveHtmlTags() {
        String input = "Some <b>bold</b> and <i>italic</i> text.";
        String cleaned = cleaner.clean(input);
        assertThat(cleaned).contains("Some bold and italic text.");
        assertThat(cleaned).doesNotContain("<b>");
    }

    @Test
    @DisplayName("should remove reference tags")
    void shouldRemoveReferenceTags() {
        String input = "Fact.<ref>Some reference</ref> More text.";
        String cleaned = cleaner.clean(input);
        assertThat(cleaned).contains("Fact.");
        assertThat(cleaned).doesNotContain("<ref>");
        assertThat(cleaned).doesNotContain("Some reference");
    }

    @Test
    @DisplayName("should remove File links")
    void shouldRemoveFileLinks() {
        String input = "[[File:Map of India.png|thumb|Map]] India is large.";
        String cleaned = cleaner.clean(input);
        assertThat(cleaned).doesNotContain("File:");
        assertThat(cleaned).contains("India is large");
    }

    @Test
    @DisplayName("should handle null input")
    void shouldHandleNullInput() {
        assertThat(cleaner.clean(null)).isEmpty();
    }

    @Test
    @DisplayName("should handle blank input")
    void shouldHandleBlankInput() {
        assertThat(cleaner.clean("   ")).isEmpty();
    }

    @Test
    @DisplayName("should collapse multiple whitespace")
    void shouldCollapseWhitespace() {
        String input = "India   is    a country.";
        String cleaned = cleaner.clean(input);
        assertThat(cleaned).isEqualTo("India is a country.");
    }
}
