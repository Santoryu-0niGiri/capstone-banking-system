
package com.capstone.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 7807 problem+json error handler shared by every WebMVC service in the

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.dao.CannotAcquireLockException.class)
    public ResponseEntity<ProblemDetail> handleDeadlock(org.springframework.dao.CannotAcquireLockException ex, WebRequest request) {
        org.slf4j.MDC.put("component", "database");
        org.slf4j.MDC.put("event_type", "DB_DEADLOCK");
        log.error("Database deadlock or lock wait timeout detected", ex);
        org.slf4j.MDC.clear();

        ProblemDetail pd = build(HttpStatus.CONFLICT, "https://capstone.bank/errors/deadlock",
                "Database Lock Conflict", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler({
        org.springframework.data.redis.RedisConnectionFailureException.class,
        org.springframework.data.redis.RedisSystemException.class,
        io.lettuce.core.RedisCommandTimeoutException.class
    })
    public ResponseEntity<ProblemDetail> handleRedisConnectionFailure(Exception ex, WebRequest request) {
        org.slf4j.MDC.put("component", "redis");
        org.slf4j.MDC.put("event_type", "REDIS_CONNECTION_TIMEOUT");
        log.error("Redis connection or command timeout", ex);
        org.slf4j.MDC.clear();

        ProblemDetail pd = build(HttpStatus.SERVICE_UNAVAILABLE, "https://capstone.bank/errors/redis-unavailable",
                "Cache Service Unavailable", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(pd);
    }

    @ExceptionHandler({
        org.springframework.dao.DataAccessResourceFailureException.class,
        org.springframework.transaction.CannotCreateTransactionException.class,
        java.sql.SQLTransientConnectionException.class
    })
    public ResponseEntity<ProblemDetail> handleDatabaseConnectionFailure(Exception ex, WebRequest request) {
        org.slf4j.MDC.put("component", "database");
        org.slf4j.MDC.put("event_type", "DB_CONNECTION_TIMEOUT");
        log.error("Database connection pool exhaustion or timeout", ex);
        org.slf4j.MDC.clear();

        ProblemDetail pd = build(HttpStatus.SERVICE_UNAVAILABLE, "https://capstone.bank/errors/db-unavailable",
                "Database Service Unavailable", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(pd);
    }

 * system. Each service's @SpringBootApplication component-scans
 * com.capstone.common so this advice is picked up automatically.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private ProblemDetail build(HttpStatus status, String type, String title, String detail, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(type));
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now());
        if (request != null) {
            pd.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        }
        return pd;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.NOT_FOUND, "https://capstone.bank/errors/not-found",
                "Resource Not Found", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateResourceException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.CONFLICT, "https://capstone.bank/errors/duplicate",
                "Duplicate Resource", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientBalance(InsufficientBalanceException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.UNPROCESSABLE_ENTITY, "https://capstone.bank/errors/insufficient-balance",
                "Insufficient Balance", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(pd);
    }

    @ExceptionHandler(LedgerPersistenceException.class)
    public ResponseEntity<ProblemDetail> handleLedgerPersistence(LedgerPersistenceException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.INTERNAL_SERVER_ERROR, "https://capstone.bank/errors/ledger-persistence",
                "Ledger Persistence Failure", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyConflictException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.CONFLICT, "https://capstone.bank/errors/idempotency-conflict",
                "Idempotency Conflict", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.UNAUTHORIZED, "https://capstone.bank/errors/invalid-credentials",
                "Invalid Credentials", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidToken(InvalidTokenException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.UNAUTHORIZED, "https://capstone.bank/errors/invalid-token",
                "Invalid Token", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.FORBIDDEN, "https://capstone.bank/errors/access-denied",
                "Access Denied", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.UNAUTHORIZED, "https://capstone.bank/errors/bad-credentials",
                "Bad Credentials", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.BAD_REQUEST, "https://capstone.bank/errors/validation",
                "Constraint Violation", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.BAD_REQUEST, "https://capstone.bank/errors/bad-request",
                "Bad Request", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.BAD_REQUEST, "https://capstone.bank/errors/invalid-state",
                "Invalid State", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, WebRequest request) {
        ProblemDetail pd = build(HttpStatus.INTERNAL_SERVER_ERROR, "https://capstone.bank/errors/internal",
                "Internal Server Error", "An unexpected error occurred: " + ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                    HttpHeaders headers,
                                                                    HttpStatusCode status,
                                                                    WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail pd = build(HttpStatus.BAD_REQUEST, "https://capstone.bank/errors/validation",
                "Validation Failed", "One or more fields failed validation", request);
        pd.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(pd);
    }
}

