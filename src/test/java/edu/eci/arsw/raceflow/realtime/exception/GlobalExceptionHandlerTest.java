package edu.eci.arsw.raceflow.realtime.exception;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesRoomNotFoundAs404() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleRoomNotFound(new RoomNotFoundException("Room not found: ABC123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Room not found: ABC123");
        assertThat(response.getBody()).containsEntry("status", 404);
    }

    @Test
    void handlesJsonProcessingAs400() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleJsonProcessing(new JsonParseException(null, "bad json"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
    }
}
