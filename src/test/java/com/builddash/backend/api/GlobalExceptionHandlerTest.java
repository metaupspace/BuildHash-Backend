package com.builddash.backend.api;

import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.PaymentGatewayException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/orders");
    }

    static class CustomEntityNotFoundException extends NotFoundException {
        public CustomEntityNotFoundException(String message) {
            super("CUSTOM_NOT_FOUND", message);
        }
    }

    @Test
    void domainExceptionSubclass_inheritsParentHttpStatus() {
        CustomEntityNotFoundException ex = new CustomEntityNotFoundException("Custom entity 123 not found");

        ResponseEntity<ApiError> response = handler.handleDomainException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("CUSTOM_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Custom entity 123 not found");
    }

    @Test
    void paymentGatewayException_mapsTo502AndSanitizesMessage() {
        UUID orderId = UUID.randomUUID();
        PaymentGatewayException ex = new PaymentGatewayException(orderId, "Connection timeout to https://secret-gw.com/api");

        ResponseEntity<ApiError> response = handler.handleDomainException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().code()).isEqualTo("PAYMENT_GATEWAY_DOWN");
        assertThat(response.getBody().message()).doesNotContain("secret-gw.com");
        assertThat(response.getBody().message()).contains("Payment gateway communication failed");
    }

    @Test
    void missingRequestHeader_mapsTo400() {
        MissingRequestHeaderException ex = new MissingRequestHeaderException(
                "Idempotency-Key", null);

        ResponseEntity<ApiError> response = handler.handleClientError(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MISSING_REQUEST_HEADER");
    }

    @Test
    void malformedJsonBody_mapsTo400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ApiError> response = handler.handleClientError(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("UNREADABLE_REQUEST");
    }

    @Test
    void typeMismatch_mapsTo400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "not-a-uuid", java.util.UUID.class, "id", null, null);

        ResponseEntity<ApiError> response = handler.handleClientError(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void oversizedUpload_mapsTo413() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10_000_000L);

        ResponseEntity<ApiError> response = handler.handleClientError(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().code()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void unsupportedMethod_mapsTo405() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<ApiError> response = handler.handleClientError(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }
}
