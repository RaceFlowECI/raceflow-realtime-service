package edu.eci.arsw.raceflow.realtime.service;

import edu.eci.arsw.raceflow.realtime.exception.RoomNotFoundException;
import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {

    private final ConcurrentHashMap<String, RoomState> rooms = new ConcurrentHashMap<>();

    public String createRoom(String creatorEmail, String creatorName) {
        String roomCode = generateRoomCode();
        RoomState room = new RoomState(roomCode, creatorEmail);
        room.getAthletes().put(creatorEmail, AthleteState.builder()
                .email(creatorEmail)
                .name(creatorName)
                .latitude(0)
                .longitude(0)
                .totalDistanceKm(0)
                .lastUpdate(null)
                .connected(false)
                .build());
        rooms.put(roomCode, room);
        return roomCode;
    }

    public RoomState joinRoom(String roomCode, String email, String name) {
        RoomState room = getRoom(roomCode);
        room.getAthletes().computeIfAbsent(email, e -> AthleteState.builder()
                .email(email)
                .name(name)
                .latitude(0)
                .longitude(0)
                .totalDistanceKm(0)
                .lastUpdate(null)
                .connected(false)
                .build());
        return room;
    }

    public RoomState getRoom(String roomCode) {
        RoomState room = rooms.get(roomCode);
        if (room == null) {
            throw new RoomNotFoundException("Room not found: " + roomCode);
        }
        return room;
    }

    public void registerSession(String roomCode, String email, WebSocketSession session) {
        RoomState room = getRoom(roomCode);
        room.getSessions().put(email, session);
        AthleteState athlete = room.getAthletes().get(email);
        if (athlete != null) {
            athlete.setConnected(true);
        }
    }

    public void unregisterSession(String roomCode, String email) {
        RoomState room = getRoom(roomCode);
        room.getSessions().remove(email);
        AthleteState athlete = room.getAthletes().get(email);
        if (athlete != null) {
            athlete.setConnected(false);
        }
    }

    private String generateRoomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
