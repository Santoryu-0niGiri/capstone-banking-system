package com.capstone.common.exception;

/**
 * Thrown when an idempotency key is currently being processed by a
 * concurrent request (as opposed to already having a cached result).
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
