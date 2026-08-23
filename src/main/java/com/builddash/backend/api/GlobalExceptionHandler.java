package com.builddash.backend.api;

import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.CheckoutValidationException;
import com.builddash.backend.domain.exception.ContractPriceOverlapException;
import com.builddash.backend.domain.exception.DomainException;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.GstRateUnresolvedException;
import com.builddash.backend.domain.exception.LockedException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.ProductNotPricedException;
import com.builddash.backend.domain.exception.SlotUnavailableException;
import com.builddash.backend.domain.exception.TooManyRequestsException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final Map<Class<? extends DomainException>, HttpStatus> STATUS_BY_EXCEPTION = Map.ofEntries(
            Map.entry(BadRequestException.class, HttpStatus.BAD_REQUEST),
            Map.entry(UnauthorizedException.class, HttpStatus.UNAUTHORIZED),
            Map.entry(ForbiddenException.class, HttpStatus.FORBIDDEN),
            Map.entry(NotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(LockedException.class, HttpStatus.LOCKED),
            Map.entry(TooManyRequestsException.class, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(SlotUnavailableException.class, HttpStatus.CONFLICT),
            Map.entry(CheckoutValidationException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ContractPriceOverlapException.class, HttpStatus.CONFLICT),
            Map.entry(ProductNotPricedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(GstRateUnresolvedException.class, HttpStatus.UNPROCESSABLE_ENTITY)
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
