package edu.eci.arsw.raceflow.realtime.service;

import edu.eci.arsw.raceflow.realtime.exception.RoomNotFoundException;
import edu.eci.arsw.raceflow.realtime.grpc.GrpcAuthClient;
import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final ConcurrentHashMap<String, RoomState> rooms = new ConcurrentHashMap<>();
    private final GrpcAuthClient grpcAuthClient;

    public RoomManager(GrpcAuthClient grpcAuthClient) {
        this.grpcAuthClient = grpcAuthClient;
    }

    public String createRoom(String creatorEmail, String creatorName) {
        String roomCode = generateRoomCode();
        RoomState room = new RoomState(roomCode, creatorEmail);
        room.getAthletes().put(creatorEmail, AthleteState.builder()
                .email(creatorEmail)
                .name(resolveAuthoritativeName(creatorEmail, creatorName))
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
                .name(resolveAuthoritativeName(email, name))
                .latitude(0)
                .longitude(0)
                .totalDistanceKm(0)
                .lastUpdate(null)
                .connected(false)
                .build());
        return room;
    }

    /**
     * Resolves the athlete's real name via gRPC against auth-service's UserProfileService,
     * instead of trusting whatever the frontend put in the request body. Falls back to the
     * client-supplied name if auth-service is unreachable or has no record for this email,
     * so a transient outage there doesn't block room creation.
     */
    private String resolveAuthoritativeName(String email, String clientSuppliedName) {
        return grpcAuthClient.lookupProfile(email)
                .map(profile -> profile.getName())
                .filter(name -> !name.isBlank())
                .orElseGet(() -> {
                    log.warn("Could not resolve authoritative name for {} via gRPC, using client-supplied value", email);
                    return clientSuppliedName;
                });
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
