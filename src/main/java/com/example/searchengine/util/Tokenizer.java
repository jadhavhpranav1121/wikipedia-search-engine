package com.example.searchengine.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tokenizes plain text into lowercase, alphabetic tokens.
 * Filters out pure-numeric tokens and tokens shorter than 2 chars.
 */
@Component
public class Tokenizer {

    private static final Pattern NON_ALPHA = Pattern.compile("[^a-zA-Z\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final StopWordFilter stopWordFilter;

    public Tokenizer(StopWordFilter stopWordFilter) {
        this.stopWordFilter = stopWordFilter;
    }

    /**
     * Tokenizes and filters the given text.
     *
     * @param text cleaned plain text
     * @return list of valid, lowercase, non-stop-word tokens
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = NON_ALPHA.matcher(text.toLowerCase(Locale.ENGLISH)).replaceAll(" ");
        String[] parts = WHITESPACE.split(normalized.strip());

        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (stopWordFilter.isIndexable(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /**
     * Tokenizes query input, preserving order for AND-query logic.
     * Does NOT apply stop-word filtering on queries so users can search short terms.
     *
     * @param query raw query string
     * @return ordered list of lowercase tokens
     */
    public List<String> tokenizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = NON_ALPHA.matcher(query.toLowerCase(Locale.ENGLISH)).replaceAll(" ");
        String[] parts = WHITESPACE.split(normalized.strip());

        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        return tokens;
    }
}
