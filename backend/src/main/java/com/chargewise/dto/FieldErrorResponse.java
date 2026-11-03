package com.chargewise.dto;

/** One invalid input field and why it was rejected. */
public record FieldErrorResponse(String field, String message) {
}
