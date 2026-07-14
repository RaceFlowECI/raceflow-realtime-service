package edu.eci.arsw.raceflow.realtime.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.arsw.raceflow.realtime.dto.PositionMessage;
import edu.eci.arsw.raceflow.realtime.dto.ReactionMessage;
import edu.eci.arsw.raceflow.realtime.dto.RoomStateMessage;
import edu.eci.arsw.raceflow.realtime.metrics.RealtimeMetrics;
import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RankingEntry;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import edu.eci.arsw.raceflow.realtime.service.RankingService;
import edu.eci.arsw.raceflow.realtime.service.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core WebSocket handler for a room's live session. One socket per athlete;
 * handles GPS position updates, reactions, room-state broadcasts, and the
 * WebRTC voice-chat signaling (join/leave/offer/answer/ICE relay). The room
 * code is extracted from the connection's URL path, and the athlete's email
 * comes from the session attribute set by {@link WebSocketAuthInterceptor}.
 */
@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);
    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("/ws/room/([^/]+)");

    private final RoomManager roomManager;
    private final RankingService rankingService;
    private final RealtimeMetrics metrics;
    private final ObjectMapper objectMapper;

    public RoomWebSocketHandler(RoomManager roomManager, RankingService rankingService,
                                 RealtimeMetrics metrics, ObjectMapper objectMapper) {
        this.roomManager = roomManager;
        this.rankingService = rankingService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /** Registers the new session with its room, marks the athlete connected, and broadcasts the updated room state. */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String email = emailOf(session);
        String roomCode = roomCodeOf(session);

        roomManager.registerSession(roomCode, email, session);
        metrics.connectionOpened();
        log.info("WS connected — room={} email={}", roomCode, email);

        broadcastRoomState(roomCode);
    }

    /** Dispatches an incoming message by its {@code type} field: POSITION, REACTION, or a VOICE_* signaling message. */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String email = emailOf(session);
        String roomCode = roomCodeOf(session);

        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.has("type") ? node.get("type").asText() : "";

        if ("POSITION".equals(type)) {
            handlePosition(roomCode, email, message.getPayload());
        } else if ("REACTION".equals(type)) {
            handleReaction(roomCode, email, message.getPayload());
        } else if ("VOICE_JOIN".equals(type)) {
            handleVoiceJoin(roomCode, email);
        } else if ("VOICE_LEAVE".equals(type)) {
            handleVoiceLeave(roomCode, email);
        } else if ("VOICE_OFFER".equals(type) || "VOICE_ANSWER".equals(type) || "VOICE_ICE".equals(type)) {
            relayVoiceSignal(roomCode, email, node);
        }
    }

    /**
     * Validates and applies a GPS position update: rejects out-of-range coordinates,
     * accumulates distance via the haversine formula against the athlete's previous
     * position, recomputes the room's ranking, and broadcasts the new state.
     */
    private void handlePosition(String roomCode, String email, String payload) throws Exception {
        PositionMessage pos = objectMapper.readValue(payload, PositionMessage.class);

        if (pos.getLatitude() < -90 || pos.getLatitude() > 90
                || pos.getLongitude() < -180 || pos.getLongitude() > 180) {
            log.warn("Invalid GPS position received — room={} email={} lat={} lng={}",
                    roomCode, email, pos.getLatitude(), pos.getLongitude());
            return;
        }

        metrics.recordPositionReceived();

        RoomState room = roomManager.getRoom(roomCode);
        List<RankingEntry> ranking;

        synchronized (room) {
            AthleteState athlete = room.getAthletes().get(email);
            if (athlete == null) {
                return;
            }

            if (athlete.getLastUpdate() != null
                    && !(athlete.getLatitude() == 0 && athlete.getLongitude() == 0)) {
                double deltaKm = rankingService.haversineKm(
                        athlete.getLatitude(), athlete.getLongitude(),
                        pos.getLatitude(), pos.getLongitude());
                athlete.setTotalDistanceKm(athlete.getTotalDistanceKm() + deltaKm);
            }

            athlete.setLatitude(pos.getLatitude());
            athlete.setLongitude(pos.getLongitude());
            athlete.setLastUpdate(LocalDateTime.now());

            ranking = rankingService.computeAndStore(room);
            metrics.recordRankingUpdate();
        }

        broadcastRoomState(roomCode, ranking);
    }

    /** Parses and broadcasts an emoji reaction from one athlete to the whole room. */
    private void handleReaction(String roomCode, String email, String payload) throws Exception {
        ReactionMessage reaction = objectMapper.readValue(payload, ReactionMessage.class);
        metrics.recordReactionSent();
        broadcastReaction(roomCode, email, reaction.getEmoji());
    }

    /** Cleans up on disconnect: leaves the voice call, unregisters the session, and broadcasts the updated room state. */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String email = emailOf(session);
        String roomCode = roomCodeOf(session);

        handleVoiceLeave(roomCode, email);
        roomManager.unregisterSession(roomCode, email);
        metrics.connectionClosed();
        log.info("WS disconnected — room={} email={} status={}", roomCode, email, status);

        broadcastRoomState(roomCode);
    }

    /**
     * WebRTC signaling: the server never touches audio — it only relays session
     * negotiation between peers (offers/answers/ICE candidates) and tracks who is
     * in the voice call so newcomers know which peers to connect to.
     */
    private void handleVoiceJoin(String roomCode, String email) throws Exception {
        RoomState room = roomManager.getRoom(roomCode);
        room.getVoiceParticipants().add(email);

        Map<String, Object> joined = new LinkedHashMap<>();
        joined.put("type", "VOICE_PEER_JOINED");
        joined.put("from", email);
        joined.put("peers", room.getVoiceParticipants());
        broadcast(room, objectMapper.writeValueAsString(joined));
    }

    /** Removes the athlete from the voice call and notifies remaining participants; no-op if they weren't in it. */
    private void handleVoiceLeave(String roomCode, String email) throws Exception {
        RoomState room = roomManager.getRoom(roomCode);
        if (!room.getVoiceParticipants().remove(email)) {
            return;
        }

        Map<String, Object> left = new LinkedHashMap<>();
        left.put("type", "VOICE_PEER_LEFT");
        left.put("from", email);
        left.put("peers", room.getVoiceParticipants());
        broadcast(room, objectMapper.writeValueAsString(left));
    }

    /** Relays a WebRTC offer/answer/ICE candidate to its target peer, stamping the authenticated sender to prevent impersonation. */
    private void relayVoiceSignal(String roomCode, String senderEmail, JsonNode node) throws Exception {
        String target = node.has("to") ? node.get("to").asText() : "";
        RoomState room = roomManager.getRoom(roomCode);
        WebSocketSession targetSession = room.getSessions().get(target);
        if (targetSession == null || !targetSession.isOpen()) {
            log.warn("Voice signal dropped — room={} from={} to={} (target not connected)",
                    roomCode, senderEmail, target);
            return;
        }

        // Re-stamp "from" with the authenticated sender so a peer can't impersonate another
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("from", senderEmail);
        targetSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(node)));
    }

    /** Recomputes the room's ranking and broadcasts the resulting room state. */
    private void broadcastRoomState(String roomCode) throws Exception {
        RoomState room = roomManager.getRoom(roomCode);
        List<RankingEntry> ranking = rankingService.computeAndStore(room);
        broadcastRoomState(roomCode, ranking);
    }

    /** Broadcasts a pre-computed ranking as a ROOM_STATE message, avoiding a redundant recomputation. */
    private void broadcastRoomState(String roomCode, List<RankingEntry> ranking) throws Exception {
        RoomState room = roomManager.getRoom(roomCode);
        RoomStateMessage msg = RoomStateMessage.builder()
                .type("ROOM_STATE")
                .roomCode(roomCode)
                .ranking(ranking)
                .timestamp(LocalDateTime.now())
                .build();

        String json = objectMapper.writeValueAsString(msg);
        broadcast(room, json);
    }

    /** Broadcasts a REACTION message to every open session in the room. */
    private void broadcastReaction(String roomCode, String senderEmail, String emoji) throws Exception {
        RoomState room = roomManager.getRoom(roomCode);
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("type", "REACTION");
        msg.put("from", senderEmail);
        msg.put("emoji", emoji);

        String json = objectMapper.writeValueAsString(msg);
        broadcast(room, json);
    }

    /** Sends a raw JSON payload to every currently-open session in the room. */
    private void broadcast(RoomState room, String json) throws Exception {
        for (WebSocketSession s : room.getSessions().values()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(json));
            }
        }
    }

    /** @return the athlete's email, as resolved at handshake time by {@link WebSocketAuthInterceptor} */
    private String emailOf(WebSocketSession session) {
        return (String) session.getAttributes().get("email");
    }

    /**
     * @return the room code extracted from the session's {@code /ws/room/{code}} URL path
     * @throws IllegalStateException if the path doesn't match the expected pattern
     */
    private String roomCodeOf(WebSocketSession session) {
        Matcher matcher = ROOM_CODE_PATTERN.matcher(session.getUri().getPath());
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Cannot extract roomCode from path: " + session.getUri().getPath());
    }
}
