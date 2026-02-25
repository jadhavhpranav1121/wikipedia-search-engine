package com.example.searchengine.exception;

/**
 * Thrown when XML parsing or indexing fails.
 */
public class IndexingException extends RuntimeException {
    public IndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}
