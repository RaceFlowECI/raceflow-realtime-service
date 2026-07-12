package edu.eci.arsw.raceflow.realtime.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.eci.arsw.raceflow.realtime.metrics.RealtimeMetrics;
import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RankingEntry;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import edu.eci.arsw.raceflow.realtime.service.RankingService;
import edu.eci.arsw.raceflow.realtime.service.RoomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomWebSocketHandlerTest {

    @Mock
    private RoomManager roomManager;

    @Mock
    private RankingService rankingService;

    @Mock
    private RealtimeMetrics metrics;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RoomWebSocketHandler handler;
    private RoomState room;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new RoomWebSocketHandler(roomManager, rankingService, metrics, objectMapper);

        room = new RoomState("ABC123", "juan@raceflow.dev");
        room.getAthletes().put("juan@raceflow.dev", AthleteState.builder()
                .email("juan@raceflow.dev").name("Juan").connected(true).build());
        when(roomManager.getRoom("ABC123")).thenReturn(room);
        when(rankingService.computeAndStore(room)).thenReturn(List.of(
                RankingEntry.builder().rank(1).email("juan@raceflow.dev").name("Juan").connected(true).build()
        ));
    }

    private WebSocketSession sessionFor(String email) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", email);
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getUri()).thenReturn(URI.create("ws://localhost:8083/ws/room/ABC123"));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void afterConnectionEstablishedRegistersSessionAndBroadcastsRoomState() throws Exception {
        WebSocketSession session = sessionFor("juan@raceflow.dev");
        room.getSessions().put("juan@raceflow.dev", session);

        handler.afterConnectionEstablished(session);

        verify(roomManager).registerSession("ABC123", "juan@raceflow.dev", session);
        verify(metrics).connectionOpened();

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.get("type").asText()).isEqualTo("ROOM_STATE");
        assertThat(payload.get("roomCode").asText()).isEqualTo("ABC123");
    }

    @Test
    void handleTextMessagePositionUpdatesAthleteAndBroadcasts() throws Exception {
        WebSocketSession session = sessionFor("juan@raceflow.dev");
        room.getSessions().put("juan@raceflow.dev", session);
        TextMessage message = new TextMessage("{\"type\":\"POSITION\",\"latitude\":4.65,\"longitude\":-74.05}");

        handler.handleTextMessage(session, message);

        verify(metrics).recordPositionReceived();
        verify(metrics).recordRankingUpdate();
        assertThat(room.getAthletes().get("juan@raceflow.dev").getLatitude()).isEqualTo(4.65);
        assertThat(room.getAthletes().get("juan@raceflow.dev").getLongitude()).isEqualTo(-74.05);
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    @Test
    void handleTextMessageDropsOutOfRangePositionWithoutBroadcasting() throws Exception {
        WebSocketSession session = sessionFor("juan@raceflow.dev");
        TextMessage message = new TextMessage("{\"type\":\"POSITION\",\"latitude\":999,\"longitude\":-74.05}");

        handler.handleTextMessage(session, message);

        verify(metrics, never()).recordPositionReceived();
        verify(roomManager, never()).getRoom(any());
        verify(session, never()).sendMessage(any());
    }

    @Test
    void handleTextMessageReactionBroadcastsToAllSessions() throws Exception {
        WebSocketSession sender = sessionFor("juan@raceflow.dev");
        WebSocketSession other = sessionFor("ana@raceflow.dev");
        room.getSessions().put("juan@raceflow.dev", sender);
        room.getSessions().put("ana@raceflow.dev", other);
        TextMessage message = new TextMessage("{\"type\":\"REACTION\",\"emoji\":\"🔥\"}");

        handler.handleTextMessage(sender, message);

        verify(metrics).recordReactionSent();

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(other).sendMessage(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.get("type").asText()).isEqualTo("REACTION");
        assertThat(payload.get("from").asText()).isEqualTo("juan@raceflow.dev");
    }

    @Test
    void voiceJoinTracksParticipantAndBroadcastsPeerList() throws Exception {
        WebSocketSession sender = sessionFor("juan@raceflow.dev");
        WebSocketSession other = sessionFor("ana@raceflow.dev");
        room.getSessions().put("juan@raceflow.dev", sender);
        room.getSessions().put("ana@raceflow.dev", other);

        handler.handleTextMessage(sender, new TextMessage("{\"type\":\"VOICE_JOIN\"}"));

        assertThat(room.getVoiceParticipants()).containsExactly("juan@raceflow.dev");

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(other).sendMessage(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.get("type").asText()).isEqualTo("VOICE_PEER_JOINED");
        assertThat(payload.get("from").asText()).isEqualTo("juan@raceflow.dev");
        assertThat(payload.get("peers")).hasSize(1);
    }

    @Test
    void voiceLeaveRemovesParticipantAndBroadcasts() throws Exception {
        WebSocketSession sender = sessionFor("juan@raceflow.dev");
        WebSocketSession other = sessionFor("ana@raceflow.dev");
        room.getSessions().put("juan@raceflow.dev", sender);
        room.getSessions().put("ana@raceflow.dev", other);
        room.getVoiceParticipants().add("juan@raceflow.dev");

        handler.handleTextMessage(sender, new TextMessage("{\"type\":\"VOICE_LEAVE\"}"));

        assertThat(room.getVoiceParticipants()).isEmpty();

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(other).sendMessage(captor.capture());
        assertThat(objectMapper.readTree(captor.getValue().getPayload()).get("type").asText())
                .isEqualTo("VOICE_PEER_LEFT");
    }

    @Test
    void voiceLeaveForNonParticipantBroadcastsNothing() throws Exception {
        WebSocketSession sender = sessionFor("juan@raceflow.dev");
        WebSocketSession other = sessionFor("ana@raceflow.dev");
        room.getSessions().put("juan@raceflow.dev", sender);
        room.getSessions().put("ana@raceflow.dev", other);

        handler.handleTextMessage(sender, new TextMessage("{\"type\":\"VOICE_LEAVE\"}"));

        verify(other, never()).sendMessage(any());
    }

    @Test
    void voiceOfferIsRelayedOnlyToTargetWithAuthenticatedSender() throws Exception {
        WebSocketSession sender = sessionFor("juan@raceflow.dev");
        WebSocketSession target = sessionFor("ana@raceflow.dev");
        WebSocketSession bystander = sessionFor("leo@raceflow.dev");
        room.getSessions().put("juan@raceflow.dev", sender);
        room.getSessions().put("ana@raceflow.dev", target);
        room.getSessions().put("leo@raceflow.dev", bystander);

        // "from" spoofed by the client — the handler must overwrite it
        handler.handleTextMessage(sender, new TextMessage(
                "{\"type\":\"VOICE_OFFER\",\"to\":\"ana@raceflow.dev\",\"from\":\"fake@x.com\",\"sdp\":\"v=0...\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(target).sendMessage(captor.capture());
        JsonNode payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.get("type").asText()).isEqualTo("VOICE_OFFER");
        assertThat(payload.get("from").asText()).isEqualTo("juan@raceflow.dev");
        assertThat(payload.get("sdp").asText()).isEqualTo("v=0...");

        verify(bystander, never()).sendMessage(any());
        verify(sender, never()).sendMessage(any());
    }

    @Test
    void voiceSignalToDisconnectedTargetIsDropped() throws Exception {
        WebSocketSession sender = sessionFor("juan@raceflow.dev");
        room.getSessions().put("juan@raceflow.dev", sender);

        handler.handleTextMessage(sender, new TextMessage(
                "{\"type\":\"VOICE_ICE\",\"to\":\"nadie@x.com\",\"candidate\":\"c\"}"));

        verify(sender, never()).sendMessage(any());
    }

    @Test
    void connectionCloseAlsoLeavesVoiceCall() throws Exception {
        WebSocketSession session = sessionFor("juan@raceflow.dev");
        WebSocketSession other = sessionFor("ana@raceflow.dev");
        room.getSessions().put("ana@raceflow.dev", other);
        room.getVoiceParticipants().add("juan@raceflow.dev");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(room.getVoiceParticipants()).isEmpty();
    }

    @Test
    void afterConnectionClosedUnregistersSessionAndBroadcasts() throws Exception {
        WebSocketSession session = sessionFor("juan@raceflow.dev");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(roomManager).unregisterSession("ABC123", "juan@raceflow.dev");
        verify(metrics).connectionClosed();
    }

    @Test
    void skipsClosedSessionsWhenBroadcasting() throws Exception {
        WebSocketSession session = sessionFor("juan@raceflow.dev");
        when(session.isOpen()).thenReturn(false);
        room.getSessions().put("juan@raceflow.dev", session);

        handler.afterConnectionEstablished(session);

        verify(session, never()).sendMessage(any());
    }
}
