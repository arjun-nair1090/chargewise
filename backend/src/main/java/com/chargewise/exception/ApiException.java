package com.chargewise.exception;

import com.chargewise.dto.FieldErrorResponse;
import java.util.List;
import org.springframework.http.HttpStatus;

/** A failure with a known HTTP status and D8 error code; the message is safe to show to clients. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final List<FieldErrorResponse> fieldErrors;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, ErrorCode code, String message, List<FieldErrorResponse> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** 404 for a missing resource or one owned by someone else, so existence is never confirmed (D7). */
    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    public static ApiException invalidState(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE, message);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed",
                List.of(new FieldErrorResponse(field, message)));
    }

    public static ApiException inputTooLarge(String message) {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.INPUT_TOO_LARGE, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<FieldErrorResponse> getFieldErrors() {
        return fieldErrors;
    }
}
