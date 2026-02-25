package com.example.searchengine.exception;

/**
 * Thrown when a search operation fails unexpectedly.
 */
public class SearchException extends RuntimeException {
    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
