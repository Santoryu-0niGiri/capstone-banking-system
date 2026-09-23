
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

