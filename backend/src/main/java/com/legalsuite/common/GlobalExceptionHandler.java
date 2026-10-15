package com.legalsuite.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns exceptions into short, plain messages. Database and stack details are logged with a
 * reference code and never sent to the browser.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleType(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("That identifier is not valid"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoRoute(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error("That action is not supported here"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiResponse.error("Send the request as JSON"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("The request is missing details or is not valid JSON"));
    }

    /** Covers NumberFormatException too: a bad amount or number in a form is the caller's mistake, not a crash. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(Exception ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("The file is too large. Files can be up to 25 MB."));
    }

    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadMultipart(Exception ex) {
        if (ex.getCause() instanceof IllegalStateException ise && ise.getMessage() != null && ise.getMessage().contains("SizeLimitExceeded")) {
            return handleTooLarge(ex);
        }
        log.debug("Bad multipart request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error("The upload was not sent as a valid file form. Choose the file and try again."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Rejected request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error("Some of the details sent are not valid. Check the form and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        String ref = reference();
        log.warn("Data integrity violation [{}]", ref, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("That could not be saved because it clashes with existing data or is too long. Reference " + ref + "."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("You do not have access to this."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleStatus(ResponseStatusException ex) {
        String reason = ex.getReason() == null ? "Request failed" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.error(reason));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        String ref = reference();
        log.error("Unhandled error [{}]", ref, ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Something went wrong on our side. Reference " + ref + "."));
    }

    private static String reference() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
