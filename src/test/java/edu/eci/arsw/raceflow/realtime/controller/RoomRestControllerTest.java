package edu.eci.arsw.raceflow.realtime.controller;

import edu.eci.arsw.raceflow.realtime.dto.CreateRoomResponse;
import edu.eci.arsw.raceflow.realtime.dto.JoinRoomResponse;
import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import edu.eci.arsw.raceflow.realtime.service.JwtService;
import edu.eci.arsw.raceflow.realtime.service.RoomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomRestControllerTest {

    @Mock
    private RoomManager roomManager;

    @Mock
    private JwtService jwtService;

    private RoomRestController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new RoomRestController(roomManager, jwtService);
        when(jwtService.extractEmail(anyString())).thenReturn("juan@raceflow.dev");
    }

    @Test
    void createReturns201WithRoomCode() {
        when(roomManager.createRoom("juan@raceflow.dev", "Juan")).thenReturn("ABC123");

        ResponseEntity<CreateRoomResponse> response = controller.create("Bearer token123", Map.of("name", "Juan"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getRoomCode()).isEqualTo("ABC123");
        assertThat(response.getBody().getCreatedBy()).isEqualTo("juan@raceflow.dev");
    }

    @Test
    void joinReturns200WithAthleteCount() {
        RoomState room = new RoomState("ABC123", "juan@raceflow.dev");
        room.getAthletes().put("juan@raceflow.dev", AthleteState.builder().email("juan@raceflow.dev").name("Juan").build());
        room.getAthletes().put("ana@raceflow.dev", AthleteState.builder().email("ana@raceflow.dev").name("Ana").build());
        when(roomManager.joinRoom(eq("ABC123"), anyString(), anyString())).thenReturn(room);

        ResponseEntity<JoinRoomResponse> response = controller.join("Bearer token123", Map.of("roomCode", "ABC123", "name", "Ana"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getRoomCode()).isEqualTo("ABC123");
        assertThat(response.getBody().getAthleteCount()).isEqualTo(2);
    }

    @Test
    void stateReturnsRoomCodeAndAthletes() {
        RoomState room = new RoomState("ABC123", "juan@raceflow.dev");
        room.getAthletes().put("juan@raceflow.dev", AthleteState.builder().email("juan@raceflow.dev").name("Juan").build());
        when(roomManager.getRoom("ABC123")).thenReturn(room);

        ResponseEntity<Map<String, Object>> response = controller.state("Bearer token123", "ABC123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("roomCode", "ABC123");
    }

    @Test
    void createStripsBearerPrefixBeforeExtractingEmail() {
        when(roomManager.createRoom(anyString(), anyString())).thenReturn("ABC123");

        controller.create("Bearer abc.def.ghi", Map.of("name", "Juan"));

        verify(jwtService).extractEmail("abc.def.ghi");
    }
}
