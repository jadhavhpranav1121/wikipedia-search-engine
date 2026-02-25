package com.example.searchengine.exception;

/**
 * Thrown when the BZ2 dump file cannot be found or read.
 */
public class DumpFileNotFoundException extends RuntimeException {
    public DumpFileNotFoundException(String path) {
        super("Wikipedia dump file not found or unreadable at path: " + path);
    }
}
