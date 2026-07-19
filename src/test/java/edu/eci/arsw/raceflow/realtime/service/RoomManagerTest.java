package edu.eci.arsw.raceflow.realtime.service;

import edu.eci.arsw.raceflow.auth.grpc.ProfileResponse;
import edu.eci.arsw.raceflow.realtime.exception.RoomNotFoundException;
import edu.eci.arsw.raceflow.realtime.grpc.GrpcAuthClient;
import edu.eci.arsw.raceflow.realtime.messaging.RoomEventPublisher;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomManagerTest {

    @Mock
    private GrpcAuthClient grpcAuthClient;

    @Mock
    private RoomEventPublisher eventPublisher;

    @InjectMocks
    private RoomManager roomManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Default: gRPC has no record for anyone, so tests exercise the client-supplied-name fallback
        // unless a specific test stubs a profile.
        when(grpcAuthClient.lookupProfile(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
    }

    @Test
    void createRoomGeneratesSixCharUppercaseCodeAndSeedsCreator() {
        String roomCode = roomManager.createRoom("juan@raceflow.dev", "Juan");

        assertThat(roomCode).hasSize(6);
        assertThat(roomCode).isEqualTo(roomCode.toUpperCase());

        RoomState room = roomManager.getRoom(roomCode);
        assertThat(room.getCreatedBy()).isEqualTo("juan@raceflow.dev");
        assertThat(room.getAthletes()).containsKey("juan@raceflow.dev");
        assertThat(room.getAthletes().get("juan@raceflow.dev").getName()).isEqualTo("Juan");
        assertThat(room.getAthletes().get("juan@raceflow.dev").isConnected()).isFalse();
    }

    @Test
    void createRoomPublishesRoomActivatedEvent() {
        String roomCode = roomManager.createRoom("juan@raceflow.dev", "Juan");

        verify(eventPublisher).publishRoomActivated(roomCode, "juan@raceflow.dev");
    }

    @Test
    void createRoomPrefersAuthoritativeNameFromGrpcOverClientSuppliedName() {
        when(grpcAuthClient.lookupProfile("juan@raceflow.dev"))
                .thenReturn(Optional.of(ProfileResponse.newBuilder()
                        .setFound(true).setEmail("juan@raceflow.dev").setName("Juan Perez").setSport("ciclismo")
                        .build()));

        String roomCode = roomManager.createRoom("juan@raceflow.dev", "NombreFalso");

        RoomState room = roomManager.getRoom(roomCode);
        assertThat(room.getAthletes().get("juan@raceflow.dev").getName()).isEqualTo("Juan Perez");
    }

    @Test
    void createRoomFallsBackToClientSuppliedNameWhenGrpcHasNoRecord() {
        // grpcAuthClient stubbed in setUp() to always return empty
        String roomCode = roomManager.createRoom("ghost@raceflow.dev", "NombreDelCliente");

        RoomState room = roomManager.getRoom(roomCode);
        assertThat(room.getAthletes().get("ghost@raceflow.dev").getName()).isEqualTo("NombreDelCliente");
    }

    @Test
    void joinRoomAddsNewAthleteToExistingRoom() {
        String roomCode = roomManager.createRoom("juan@raceflow.dev", "Juan");

        RoomState room = roomManager.joinRoom(roomCode, "ana@raceflow.dev", "Ana");

        assertThat(room.getAthletes()).containsKeys("juan@raceflow.dev", "ana@raceflow.dev");
        assertThat(room.getAthletes()).hasSize(2);
    }

    @Test
    void joinRoomIsIdempotentForExistingAthlete() {
        String roomCode = roomManager.createRoom("juan@raceflow.dev", "Juan");

        roomManager.joinRoom(roomCode, "juan@raceflow.dev", "Juan Renamed");

        RoomState room = roomManager.getRoom(roomCode);
        assertThat(room.getAthletes()).hasSize(1);
        assertThat(room.getAthletes().get("juan@raceflow.dev").getName()).isEqualTo("Juan");
    }

    @Test
    void joinRoomThrowsWhenRoomDoesNotExist() {
        assertThatThrownBy(() -> roomManager.joinRoom("NOPE00", "ana@raceflow.dev", "Ana"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void getRoomThrowsWhenRoomDoesNotExist() {
        assertThatThrownBy(() -> roomManager.getRoom("NOPE00"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void registerSessionMarksAthleteConnectedAndStoresSession() {
        String roomCode = roomManager.createRoom("juan@raceflow.dev", "Juan");
        WebSocketSession session = mock(WebSocketSession.class);

        roomManager.registerSession(roomCode, "juan@raceflow.dev", session);

        RoomState room = roomManager.getRoom(roomCode);
        assertThat(room.getSessions()).containsEntry("juan@raceflow.dev", session);
        assertThat(room.getAthletes().get("juan@raceflow.dev").isConnected()).isTrue();
    }

    @Test
    void unregisterSessionMarksAthleteDisconnectedAndRemovesSession() {
        String roomCode = roomManager.createRoom("juan@raceflow.dev", "Juan");
        WebSocketSession session = mock(WebSocketSession.class);
        roomManager.registerSession(roomCode, "juan@raceflow.dev", session);

        roomManager.unregisterSession(roomCode, "juan@raceflow.dev");

        RoomState room = roomManager.getRoom(roomCode);
        assertThat(room.getSessions()).doesNotContainKey("juan@raceflow.dev");
        assertThat(room.getAthletes().get("juan@raceflow.dev").isConnected()).isFalse();
    }
}
