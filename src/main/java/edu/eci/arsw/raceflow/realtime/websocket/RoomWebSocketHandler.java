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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String email = emailOf(session);
        String roomCode = roomCodeOf(session);

        roomManager.registerSession(roomCode, email, session);
        metrics.connectionOpened();
        log.info("WS connected — room={} email={}", roomCode, email);

        broadcastRoomState(roomCode);
    }

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
        }
    }

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

    private void handleReaction(String roomCode, String email, String payload) throws Exception {
        ReactionMessage reaction = objectMapper.readValue(payload, ReactionMessage.class);
        metrics.recordReactionSent();
        broadcastReaction(roomCode, email, reaction.getEmoji());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String email = emailOf(session);
        String roomCode = roomCodeOf(session);

        roomManager.unregisterSession(roomCode, email);
        metrics.connectionClosed();
        log.info("WS disconnected — room={} email={} status={}", roomCode, email, status);

        broadcastRoomState(roomCode);
    }

    private void broadcastRoomState(String roomCode) throws Exception {
        RoomState room = roomManager.getRoom(roomCode);
        List<RankingEntry> ranking = rankingService.computeAndStore(room);
        broadcastRoomState(roomCode, ranking);
    }

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

    private void broadcastReaction(String roomCode, String senderEmail, String emoji) throws Exception {
        RoomState room = roomManager.getRoom(roomCode);
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("type", "REACTION");
        msg.put("from", senderEmail);
        msg.put("emoji", emoji);

        String json = objectMapper.writeValueAsString(msg);
        broadcast(room, json);
    }

    private void broadcast(RoomState room, String json) throws Exception {
        for (WebSocketSession s : room.getSessions().values()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(json));
            }
        }
    }

    private String emailOf(WebSocketSession session) {
        return (String) session.getAttributes().get("email");
    }

    private String roomCodeOf(WebSocketSession session) {
        Matcher matcher = ROOM_CODE_PATTERN.matcher(session.getUri().getPath());
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Cannot extract roomCode from path: " + session.getUri().getPath());
    }
}
