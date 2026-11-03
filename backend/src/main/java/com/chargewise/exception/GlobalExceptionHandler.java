package com.chargewise.exception;

import com.chargewise.dto.ApiErrorResponse;
import com.chargewise.dto.FieldErrorResponse;
import com.chargewise.service.demo.DatasetValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Maps every failure to the D8 error body. Messages come only from this application's own exceptions;
 * unexpected errors are logged server-side and reported as a generic 500 without details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_MESSAGE = "Request validation failed";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApi(ApiException e, HttpServletRequest request) {
        return body(e.getStatus(), e.getCode(), e.getMessage(), e.getFieldErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<FieldErrorResponse> fields = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        return validation(fields, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidParameters(HandlerMethodValidationException e,
            HttpServletRequest request) {
        List<FieldErrorResponse> fields = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorResponse(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return validation(fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException e,
            HttpServletRequest request) {
        List<FieldErrorResponse> fields = e.getConstraintViolations().stream()
                .map(v -> new FieldErrorResponse(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validation(fields, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        if (e.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            String field = jackson.getPath().stream()
                    .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining(".")).replace(".[", "[");
            return validation(List.of(new FieldErrorResponse(field, unreadableReason(jackson))), request);
        }
        return body(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request body is missing or not valid JSON",
                List.of(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
            HttpServletRequest request) {
        return validation(List.of(new FieldErrorResponse(e.getName(), "has an invalid value")), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException e,
            HttpServletRequest request) {
        return validation(List.of(new FieldErrorResponse(e.getParameterName(), "is required")), request);
    }

    @ExceptionHandler(DatasetValidationException.class)
    ResponseEntity<ApiErrorResponse> handleDataset(DatasetValidationException e, HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, e.getMessage(), List.of(), request);
    }

    /** Database safeguards (unique keys, D6 exclusion constraints) surface as 409 with a readable reason. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        String message;
        if (detail.contains("bookings_no_vehicle_overlap")) {
            message = "The vehicle already has an active booking overlapping this window";
        } else if (detail.contains("bookings_no_connector_overlap")) {
            message = "The connector is already booked in this window";
        } else if (detail.contains("registration_number")) {
            message = "A vehicle with this registration number already exists";
        } else if (detail.contains("email")) {
            message = "An account with this email already exists";
        } else {
            log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
            message = "The request conflicts with existing data";
        }
        return body(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message, List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return body(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You do not have permission to perform this action",
                List.of(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiErrorResponse> handleSpringMvc(Exception e, HttpServletRequest request) {
        HttpStatusCode status = ((ErrorResponse) e).getStatusCode();
        ErrorCode code = switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ErrorCode.VALIDATION_FAILED;
        };
        String message = switch (code) {
            case NOT_FOUND -> "No endpoint exists at this path";
            case METHOD_NOT_ALLOWED -> "This endpoint does not support " + request.getMethod();
            case UNSUPPORTED_MEDIA_TYPE -> "Content type must be application/json";
            default -> VALIDATION_MESSAGE;
        };
        return body(HttpStatus.valueOf(status.value()), code, message, List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred",
                List.of(), request);
    }

    private static String unreadableReason(JacksonException e) {
        Class<?> target = e instanceof MismatchedInputException mismatch ? mismatch.getTargetType() : null;
        if (target == Instant.class) {
            return "must be an ISO-8601 timestamp with an explicit offset, e.g. 2026-11-05T10:00:00Z";
        }
        if (target != null && target.isEnum()) {
            return "must be one of " + java.util.Arrays.toString(target.getEnumConstants());
        }
        return "has an invalid value";
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }

    private static ResponseEntity<ApiErrorResponse> validation(List<FieldErrorResponse> fields,
            HttpServletRequest request) {
        List<FieldErrorResponse> sorted = fields.stream()
                .sorted(Comparator.comparing(FieldErrorResponse::field)).toList();
        return body(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, sorted, request);
    }

    static ResponseEntity<ApiErrorResponse> body(HttpStatus status, ErrorCode code, String message,
            List<FieldErrorResponse> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), code.name(), message,
                fields, Instant.now(), request.getRequestURI()));
    }
}
