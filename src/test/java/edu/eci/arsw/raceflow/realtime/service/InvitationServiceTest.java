package edu.eci.arsw.raceflow.realtime.service;

import edu.eci.arsw.raceflow.realtime.dto.RoomInvitation;
import edu.eci.arsw.raceflow.realtime.exception.RoomNotFoundException;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class InvitationServiceTest {

    @Mock
    private RoomManager roomManager;

    private InvitationService service;
    private RoomState room;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new InvitationService(roomManager);
        room = new RoomState("ABC123", "juan@raceflow.dev");
        when(roomManager.getRoom("ABC123")).thenReturn(room);
    }

    @Test
    void inviteStoresPendingInvitationForInvitee() {
        service.invite("ABC123", "juan@raceflow.dev", "Juan", "ana@raceflow.dev");

        List<RoomInvitation> pending = service.pendingFor("ana@raceflow.dev");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getRoomCode()).isEqualTo("ABC123");
        assertThat(pending.get(0).getFromName()).isEqualTo("Juan");
    }

    @Test
    void inviteIsIdempotentPerRoomAndInvitee() {
        service.invite("ABC123", "juan@raceflow.dev", "Juan", "ana@raceflow.dev");
        service.invite("ABC123", "juan@raceflow.dev", "Juan", "ana@raceflow.dev");

        assertThat(service.pendingFor("ana@raceflow.dev")).hasSize(1);
    }

    @Test
    void inviteeEmailIsCaseInsensitive() {
        service.invite("ABC123", "juan@raceflow.dev", "Juan", "Ana@RaceFlow.dev");

        assertThat(service.pendingFor("ana@raceflow.dev")).hasSize(1);
    }

    @Test
    void inviteToMissingRoomFails() {
        when(roomManager.getRoom("NOPE99")).thenThrow(new RoomNotFoundException("Room not found"));

        assertThatThrownBy(() -> service.invite("NOPE99", "juan@raceflow.dev", "Juan", "ana@raceflow.dev"))
                .isInstanceOf(RoomNotFoundException.class);
        assertThat(service.pendingFor("ana@raceflow.dev")).isEmpty();
    }

    @Test
    void invitationsForDeadRoomsAreDiscarded() {
        service.invite("ABC123", "juan@raceflow.dev", "Juan", "ana@raceflow.dev");
        when(roomManager.getRoom("ABC123")).thenThrow(new RoomNotFoundException("gone"));

        assertThat(service.pendingFor("ana@raceflow.dev")).isEmpty();
        assertThat(service.pendingFor("ana@raceflow.dev")).isEmpty();
    }

    @Test
    void discardRemovesInvitation() {
        service.invite("ABC123", "juan@raceflow.dev", "Juan", "ana@raceflow.dev");

        service.discard("ana@raceflow.dev", "ABC123");

        assertThat(service.pendingFor("ana@raceflow.dev")).isEmpty();
    }

    @Test
    void pendingForUnknownEmailIsEmpty() {
        assertThat(service.pendingFor("nadie@x.com")).isEmpty();
    }
}
