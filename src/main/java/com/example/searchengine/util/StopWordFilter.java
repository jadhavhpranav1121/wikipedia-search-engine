package com.example.searchengine.util;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Injectable stop-word filter. Using a Spring-managed component (not static util)
 * so it can be mocked in tests and potentially loaded from a configurable source.
 */
@Component
public class StopWordFilter {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "it", "its", "be", "as", "was",
            "are", "were", "been", "has", "have", "had", "do", "does", "did",
            "will", "would", "could", "should", "may", "might", "shall", "can",
            "not", "no", "nor", "so", "yet", "both", "either", "neither",
            "that", "this", "these", "those", "which", "who", "whom", "what",
            "when", "where", "why", "how", "all", "any", "each", "every",
            "few", "more", "most", "other", "some", "such", "than", "then",
            "into", "through", "during", "before", "after", "above", "below",
            "between", "out", "off", "over", "under", "again", "further",
            "i", "me", "my", "we", "our", "you", "your", "he", "she", "they",
            "his", "her", "their", "them", "him", "us", "about", "up", "also",
            "if", "only", "same", "s", "t", "just", "don", "now"
    );

    /**
     * Returns true if the token is a stop word.
     *
     * @param token lowercase token to check
     * @return true if it is a stop word
     */
    public boolean isStopWord(String token) {
        return STOP_WORDS.contains(token);
    }

    /**
     * Returns true if the token should be indexed (non-stop, length >= 2).
     *
     * @param token lowercase token
     * @return true if the token is indexable
     */
    public boolean isIndexable(String token) {
        return token != null
                && token.length() >= 2
                && !isStopWord(token);
    }
}
