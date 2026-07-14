package edu.eci.arsw.raceflow.realtime.model;

import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state of one training room: its athletes, their live WebSocket
 * sessions, and who is currently in the voice call. Deliberately not
 * persisted -- rooms are ephemeral training sessions, and this is why
 * realtime-service is pinned to a single instance.
 */
@Data
public class RoomState {
    private final String roomCode;
    private final String createdBy;
    private final ConcurrentHashMap<String, AthleteState> athletes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Set<String> voiceParticipants = ConcurrentHashMap.newKeySet();
    private final LocalDateTime createdAt = LocalDateTime.now();

    /**
     * @param roomCode  el código único de 6 caracteres de la sala
     * @param createdBy el email del atleta que creó la sala
     */
    public RoomState(String roomCode, String createdBy) {
        this.roomCode = roomCode;
        this.createdBy = createdBy;
    }
}
