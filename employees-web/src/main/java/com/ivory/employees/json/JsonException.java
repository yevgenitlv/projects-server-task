package com.ivory.employees.json;

/** Thrown when a JSON document cannot be parsed. */
public class JsonException extends RuntimeException {

    public JsonException(String message) {
        super(message);
    }
}
