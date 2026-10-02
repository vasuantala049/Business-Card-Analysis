package com.vasu.cardanalyzer.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleDuplicateCardKeepsNullFieldsWithoutThrowing() {
        DuplicateBusinessCardException ex = new DuplicateBusinessCardException(
                null,
                null,
                null,
                List.of("matching email")
        );

        ResponseEntity<Map<String, Object>> response = handler.handleDuplicateCard(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals("Duplicate business card", body.get("error"));
        assertEquals(ex.getMessage(), body.get("message"));
        assertNull(body.get("duplicateCardId"));
        assertNull(body.get("duplicateCardName"));
        assertNull(body.get("duplicateCardCompany"));
        assertEquals(List.of("matching email"), body.get("reasons"));
    }
}
