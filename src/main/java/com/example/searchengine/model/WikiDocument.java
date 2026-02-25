package com.example.searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a parsed Wikipedia article stored in the document store.
 * This is the core domain object holding raw and cleaned text.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WikiDocument {

    /** Unique numeric ID extracted from the Wikipedia XML dump. */
    private long id;

    /** Article title. */
    private String title;

    /** Cleaned plain text (wiki markup removed). */
    private String cleanText;

    /** Total number of tokens in this document (used for TF normalization). */
    private int tokenCount;
}
