package com.builddash.backend.api;

import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.CheckoutValidationException;
import com.builddash.backend.domain.exception.ContractPriceOverlapException;
import com.builddash.backend.domain.exception.DeleteRequestPendingException;
import com.builddash.backend.domain.exception.DomainException;
import com.builddash.backend.domain.exception.DuplicateQuoteException;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.GstRateUnresolvedException;
import com.builddash.backend.domain.exception.InvalidOrderStateException;
import com.builddash.backend.domain.exception.InvalidReturnStateException;
import com.builddash.backend.domain.exception.InvalidPoStateException;
import com.builddash.backend.domain.exception.InvalidApprovalStateException;
import com.builddash.backend.domain.exception.ApprovalPolicyValidationException;
import com.builddash.backend.domain.exception.InvalidRfqStateException;
import com.builddash.backend.domain.exception.InvalidSupportTicketStateException;
import com.builddash.backend.domain.exception.LastOwnerProtectedException;
import com.builddash.backend.domain.exception.LockedException;
import com.builddash.backend.domain.exception.MemberAlreadyExistsException;
import com.builddash.backend.domain.exception.ModificationWindowExpiredException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.PaymentRetryInProgressException;
import com.builddash.backend.domain.exception.ProductNotPricedException;
import com.builddash.backend.domain.exception.QuoteValidationException;
import com.builddash.backend.domain.exception.ReturnAlreadyExistsException;
import com.builddash.backend.domain.exception.RfqValidationException;
import com.builddash.backend.domain.exception.SlotUnavailableException;
import com.builddash.backend.domain.exception.SiteInUseException;
import com.builddash.backend.domain.exception.SiteNameTakenException;
import com.builddash.backend.domain.exception.OwnerPermissionsImmutableException;
import com.builddash.backend.domain.exception.PermissionEscalationGuardException;
import com.builddash.backend.domain.exception.PoAttachmentExistsException;
import com.builddash.backend.domain.exception.PoImportValidationException;
import com.builddash.backend.domain.exception.PoUploadInProgressException;
import com.builddash.backend.domain.exception.TooManyRequestsException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.exception.VendorNotRoutableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;

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
            Map.entry(GstRateUnresolvedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(InvalidOrderStateException.class, HttpStatus.CONFLICT),
            Map.entry(InvalidReturnStateException.class, HttpStatus.CONFLICT),
            Map.entry(ReturnAlreadyExistsException.class, HttpStatus.CONFLICT),
            Map.entry(DeleteRequestPendingException.class, HttpStatus.CONFLICT),
            Map.entry(InvalidSupportTicketStateException.class, HttpStatus.CONFLICT),
            Map.entry(ModificationWindowExpiredException.class, HttpStatus.CONFLICT),
            Map.entry(PaymentRetryInProgressException.class, HttpStatus.CONFLICT),
            // Phase 9-A: company foundation / 9-A.1: permission model
            Map.entry(MemberAlreadyExistsException.class, HttpStatus.CONFLICT),
            Map.entry(SiteInUseException.class, HttpStatus.CONFLICT),
            Map.entry(SiteNameTakenException.class, HttpStatus.CONFLICT),
            Map.entry(LastOwnerProtectedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(OwnerPermissionsImmutableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(PermissionEscalationGuardException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            // Phase 9-B: RFQ lifecycle / vendor routing / controlled quotes
            Map.entry(InvalidRfqStateException.class, HttpStatus.CONFLICT),
            Map.entry(DuplicateQuoteException.class, HttpStatus.CONFLICT),
            Map.entry(RfqValidationException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(QuoteValidationException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(VendorNotRoutableException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            // Phase 9-C: PO attachments / bulk import / draft conversion
            Map.entry(InvalidPoStateException.class, HttpStatus.CONFLICT),
            Map.entry(PoAttachmentExistsException.class, HttpStatus.CONFLICT),
            Map.entry(PoUploadInProgressException.class, HttpStatus.CONFLICT),
            Map.entry(PoImportValidationException.class, HttpStatus.BAD_REQUEST),
            // Phase 9-D: order approval gate
            Map.entry(InvalidApprovalStateException.class, HttpStatus.CONFLICT),
            Map.entry(ApprovalPolicyValidationException.class, HttpStatus.BAD_REQUEST)
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

    /**
     * Framework-raised client errors that must not fall through to the 500 catch-all.
     * Without these, a missing header or malformed JSON returns INTERNAL_ERROR.
     */
    @ExceptionHandler({
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            HttpRequestMethodNotSupportedException.class,
            MaxUploadSizeExceededException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiError> handleClientError(Exception ex, HttpServletRequest request) {
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request);
        }
        if (ex instanceof MaxUploadSizeExceededException) {
            return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", ex.getMessage(), request);
        }
        if (ex instanceof MissingRequestHeaderException missing) {
            return build(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_HEADER",
                    "Missing required header: " + missing.getHeaderName(), request);
        }
        if (ex instanceof MissingServletRequestParameterException missing) {
            return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                    "Missing required parameter: " + missing.getParameterName(), request);
        }
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                    "Invalid value for parameter: " + mismatch.getName(), request);
        }
        if (ex instanceof org.springframework.web.method.annotation.HandlerMethodValidationException) {
            return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), request);
        }
        return build(HttpStatus.BAD_REQUEST, "UNREADABLE_REQUEST", "Malformed request body", request);
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
