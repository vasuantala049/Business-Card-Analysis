package com.vasu.cardanalyzer.exception;

import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.mongodb.MongoException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({WebClientRequestException.class})
    public ResponseEntity<Map<String, String>> handleMlServiceUnavailable(WebClientRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "error", "ML service is unavailable",
                        "message", "Could not reach the ML service. Make sure ml-service is running and ML_SERVICE_URL is correct."
                ));
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, MongoException.class, DataAccessException.class})
    public ResponseEntity<Map<String, String>> handleDatabaseUnavailable(Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Database is unavailable",
                        "message", "Could not reach MongoDB. Check Atlas access, network, and credentials."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Unexpected server error",
                        "message", "Something went wrong while processing the request."
                ));
    }
}