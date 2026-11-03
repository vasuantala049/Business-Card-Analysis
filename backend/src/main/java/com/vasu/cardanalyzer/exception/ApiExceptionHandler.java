package com.vasu.cardanalyzer.exception;

import java.util.LinkedHashMap;
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
        public ResponseEntity<Map<String, String>> handleQwenServiceUnavailable(WebClientRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                                                                "error", "Qwen service is unavailable",
                                "message", "Could not reach the Qwen service. Make sure QWEN_BASE_URL is correct and the endpoint is running."
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

    @ExceptionHandler(DuplicateBusinessCardException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateCard(DuplicateBusinessCardException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Duplicate business card");
        body.put("message", ex.getMessage());
        body.put("duplicateCardId", ex.getExistingCardId());
        body.put("duplicateCardName", ex.getExistingName());
        body.put("duplicateCardCompany", ex.getExistingCompany());
        body.put("reasons", ex.getReasons());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", ex.getClass().getSimpleName(),
                        "message", ex.getMessage() != null ? ex.getMessage() : "Something went wrong while processing the request."
                ));
    }
}