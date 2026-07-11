package edu.eci.arsw.raceflow.realtime.model;

import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class RoomState {
    private final String roomCode;
    private final String createdBy;
    private final ConcurrentHashMap<String, AthleteState> athletes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Set<String> voiceParticipants = ConcurrentHashMap.newKeySet();
    private final LocalDateTime createdAt = LocalDateTime.now();

    public RoomState(String roomCode, String createdBy) {
        this.roomCode = roomCode;
        this.createdBy = createdBy;
    }
}
