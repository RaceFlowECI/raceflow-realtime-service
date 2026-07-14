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
 * Manejador central de WebSocket para la sesión en vivo de una sala. Un socket por atleta;
 * maneja actualizaciones de posición GPS, reacciones, transmisiones del estado de la sala, y
 * la señalización del chat de voz WebRTC (unirse/salir/oferta/respuesta/relevo ICE). El código
 * de la sala se extrae de la ruta URL de la conexión, y el email del atleta viene del
 * atributo de sesión establecido por {@link WebSocketAuthInterceptor}.
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

    /** Registra la nueva sesión con su sala, marca al atleta como conectado, y transmite el estado actualizado de la sala. */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String email = emailOf(session);
        String roomCode = roomCodeOf(session);

        roomManager.registerSession(roomCode, email, session);
        metrics.connectionOpened();
        log.info("WS connected — room={} email={}", roomCode, email);

        broadcastRoomState(roomCode);
    }

    /** Despacha un mensaje entrante según su campo {@code type}: POSITION, REACTION, o un mensaje de señalización VOICE_*. */
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
     * Valida y aplica una actualización de posición GPS: rechaza coordenadas fuera de rango,
     * acumula distancia mediante la fórmula de haversine contra la posición anterior del atleta,
     * recalcula el ranking de la sala, y transmite el nuevo estado.
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

    /** Parsea y transmite una reacción con emoji de un atleta a toda la sala. */
    private void handleReaction(String roomCode, String email, String payload) throws Exception {
        ReactionMessage reaction = objectMapper.readValue(payload, ReactionMessage.class);
        metrics.recordReactionSent();
        broadcastReaction(roomCode, email, reaction.getEmoji());
    }

    /** Limpia al desconectarse: sale de la llamada de voz, desregistra la sesión, y transmite el estado actualizado de la sala. */
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
     * Señalización WebRTC: el servidor nunca toca el audio -- solo transmite la negociación
     * de sesión entre pares (ofertas/respuestas/candidatos ICE) y lleva registro de quién está
     * en la llamada de voz para que los recién llegados sepan a qué pares conectarse.
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

    /** Elimina al atleta de la llamada de voz y notifica a los participantes restantes; no hace nada si no estaba en ella. */
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

    /** Transmite una oferta/respuesta/candidato ICE de WebRTC a su par destino, estampando el remitente autenticado para evitar suplantación. */
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

    /** Recalcula el ranking de la sala y transmite el estado de sala resultante. */
    private void broadcastRoomState(String roomCode) throws Exception {
        RoomState room = roomManager.getRoom(roomCode);
        List<RankingEntry> ranking = rankingService.computeAndStore(room);
        broadcastRoomState(roomCode, ranking);
    }

    /** Transmite un ranking ya calculado como un mensaje ROOM_STATE, evitando un recálculo redundante. */
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

    /** Transmite un mensaje REACTION a cada sesión abierta en la sala. */
    private void broadcastReaction(String roomCode, String senderEmail, String emoji) throws Exception {
        RoomState room = roomManager.getRoom(roomCode);
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("type", "REACTION");
        msg.put("from", senderEmail);
        msg.put("emoji", emoji);

        String json = objectMapper.writeValueAsString(msg);
        broadcast(room, json);
    }

    /** Envía un payload JSON crudo a cada sesión actualmente abierta en la sala. */
    private void broadcast(RoomState room, String json) throws Exception {
        for (WebSocketSession s : room.getSessions().values()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(json));
            }
        }
    }

    /** @return el email del atleta, tal como se resolvió en el momento del handshake por {@link WebSocketAuthInterceptor} */
    private String emailOf(WebSocketSession session) {
        return (String) session.getAttributes().get("email");
    }

    /**
     * @return el código de sala extraído de la ruta URL {@code /ws/room/{code}} de la sesión
     * @throws IllegalStateException si la ruta no coincide con el patrón esperado
     */
    private String roomCodeOf(WebSocketSession session) {
        Matcher matcher = ROOM_CODE_PATTERN.matcher(session.getUri().getPath());
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Cannot extract roomCode from path: " + session.getUri().getPath());
    }
}
