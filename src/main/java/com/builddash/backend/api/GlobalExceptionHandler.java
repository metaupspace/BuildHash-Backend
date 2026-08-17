package com.builddash.backend.api;

import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.DomainException;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.LockedException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.TooManyRequestsException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Domain exceptions carry no HttpStatus themselves (domain code stays ignorant of
     * HTTP) — this is the one place that maps each business-rule-violation type to its
     * wire-level status.
     */
    private static final Map<Class<? extends DomainException>, HttpStatus> STATUS_BY_EXCEPTION = Map.of(
            BadRequestException.class, HttpStatus.BAD_REQUEST,
            UnauthorizedException.class, HttpStatus.UNAUTHORIZED,
            ForbiddenException.class, HttpStatus.FORBIDDEN,
            NotFoundException.class, HttpStatus.NOT_FOUND,
            LockedException.class, HttpStatus.LOCKED,
            TooManyRequestsException.class, HttpStatus.TOO_MANY_REQUESTS
    );

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomainException(DomainException ex, HttpServletRequest request) {
        HttpStatus status = STATUS_BY_EXCEPTION.getOrDefault(ex.getClass(), HttpStatus.INTERNAL_SERVER_ERROR);
        return build(status, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    /**
     * Covers malformed request input that isn't a validation-annotated field — e.g. a
     * non-UUID value in a filter query param. Path-variable resource ids are handled
     * separately by the controller (a malformed id is treated as "not found", not "bad
     * request") before this ever fires for those.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
