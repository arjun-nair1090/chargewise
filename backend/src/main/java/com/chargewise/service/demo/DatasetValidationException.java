package com.chargewise.service.demo;

/** A demo dataset is empty, malformed or inconsistent; the message names the file and offending record. */
public class DatasetValidationException extends RuntimeException {

    public DatasetValidationException(String message) {
        super(message);
    }
}
