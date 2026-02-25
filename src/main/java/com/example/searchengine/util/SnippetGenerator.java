package com.example.searchengine.util;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Generates a context-aware snippet from a document's clean text.
 * Tries to find the first occurrence of any query token and center the snippet around it.
 */
@Component
public class SnippetGenerator {

    /**
     * Generates a snippet from the given text.
     *
     * @param cleanText      the cleaned document text
     * @param queryTokens    the tokenized query terms
     * @param snippetLength  maximum character length of the snippet
     * @return a trimmed snippet string
     */
    public String generate(String cleanText, List<String> queryTokens, int snippetLength) {
        if (cleanText == null || cleanText.isBlank()) {
            return "";
        }

        String lower = cleanText.toLowerCase(Locale.ENGLISH);

        int bestPosition = -1;
        for (String token : queryTokens) {
            int idx = lower.indexOf(token);
            if (idx != -1) {
                bestPosition = idx;
                break;
            }
        }

        int start;
        if (bestPosition == -1) {
            // No match found — just take the beginning
            start = 0;
        } else {
            // Center the snippet around the match, with some leading context
            start = Math.max(0, bestPosition - snippetLength / 4);
        }

        int end = Math.min(cleanText.length(), start + snippetLength);

        // Adjust start backwards to avoid cutting mid-word
        while (start > 0 && cleanText.charAt(start) != ' ') {
            start--;
        }

        String snippet = cleanText.substring(start, end).strip();

        // Ellipsis handling
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < cleanText.length()) {
            snippet = snippet + "...";
        }

        return snippet;
    }
}
