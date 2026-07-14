package edu.eci.arsw.raceflow.realtime.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translates domain exceptions into consistent JSON error responses across the API. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @return 404 Not Found for an operation on a nonexistent room */
    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRoomNotFound(RoomNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage(), 404));
    }

    /** @return 400 Bad Request for a malformed JSON payload */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleJsonProcessing(JsonProcessingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("Malformed JSON payload", 400));
    }

    private Map<String, Object> errorBody(String message, int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("status", status);
        return body;
    }
}
