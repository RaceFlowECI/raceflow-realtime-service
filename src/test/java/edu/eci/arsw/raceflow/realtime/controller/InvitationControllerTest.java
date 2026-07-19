package edu.eci.arsw.raceflow.realtime.controller;

import edu.eci.arsw.raceflow.realtime.dto.RoomInvitation;
import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import edu.eci.arsw.raceflow.realtime.service.InvitationService;
import edu.eci.arsw.raceflow.realtime.service.JwtService;
import edu.eci.arsw.raceflow.realtime.service.RoomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InvitationControllerTest {

    @Mock
    private InvitationService invitationService;

    @Mock
    private RoomManager roomManager;

    @Mock
    private JwtService jwtService;

    private InvitationController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new InvitationController(invitationService, roomManager, jwtService);
        when(jwtService.extractEmail("token123")).thenReturn("juan@raceflow.dev");
    }

    @Test
    void inviteResolvesSenderNameFromRoomState() {
        RoomState room = new RoomState("ABC123", "juan@raceflow.dev");
        room.getAthletes().put("juan@raceflow.dev",
                AthleteState.builder().email("juan@raceflow.dev").name("Juan").build());
        when(roomManager.getRoom("ABC123")).thenReturn(room);

        var response = controller.invite("Bearer token123", "ABC123", Map.of("email", "ana@raceflow.dev"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(invitationService).invite("ABC123", "juan@raceflow.dev", "Juan", "ana@raceflow.dev");
    }

    @Test
    void inviteFallsBackToEmailWhenSenderNotInRoom() {
        when(roomManager.getRoom("ABC123")).thenReturn(new RoomState("ABC123", "otro@x.com"));

        controller.invite("Bearer token123", "ABC123", Map.of("email", "ana@raceflow.dev"));

        verify(invitationService).invite("ABC123", "juan@raceflow.dev", "juan@raceflow.dev", "ana@raceflow.dev");
    }

    @Test
    void myInvitationsReturnsPendingList() {
        when(invitationService.pendingFor("juan@raceflow.dev"))
                .thenReturn(List.of(RoomInvitation.builder().roomCode("ABC123").build()));

        var response = controller.myInvitations("Bearer token123");

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void declineDiscardsAndReturns204() {
        var response = controller.decline("Bearer token123", "ABC123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(invitationService).discard("juan@raceflow.dev", "ABC123");
    }
}
