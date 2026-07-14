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

/**
 * Es dueño del ciclo de vida en memoria de todas las salas activas: creación, unión, y
 * seguimiento de la sesión WebSocket en vivo de cada atleta. Las salas solo existen en la
 * memoria de este proceso, por eso realtime-service debe ejecutarse como una sola instancia.
 */
@Service
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final ConcurrentHashMap<String, RoomState> rooms = new ConcurrentHashMap<>();
    private final GrpcAuthClient grpcAuthClient;

    /**
     * @param grpcAuthClient usado para resolver el nombre autoritativo de cada atleta desde auth-service
     */
    public RoomManager(GrpcAuthClient grpcAuthClient) {
        this.grpcAuthClient = grpcAuthClient;
    }

    /**
     * Crea una nueva sala con un código de 6 caracteres recién generado y añade al
     * creador como su primer atleta.
     *
     * @param creatorEmail el email del creador
     * @param creatorName  el nombre suministrado por el cliente, usado solo como respaldo si
     *                     la búsqueda del nombre por gRPC falla
     * @return el código de la nueva sala
     */
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

    /**
     * Añade un atleta a una sala existente si aún no está presente.
     *
     * @param roomCode la sala a la que se une
     * @param email    el email del atleta que se une
     * @param name     el nombre suministrado por el cliente, usado solo como respaldo
     * @return el estado de la sala a la que se unió
     * @throws edu.eci.arsw.raceflow.realtime.exception.RoomNotFoundException si la sala no existe
     */
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
     * Resuelve el nombre real del atleta vía gRPC contra el UserProfileService de auth-service,
     * en vez de confiar en lo que el frontend puso en el cuerpo de la petición. Usa como respaldo
     * el nombre suministrado por el cliente si auth-service no está disponible o no tiene registro
     * para este email, para que una caída transitoria no bloquee la creación de la sala.
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

    /**
     * @param roomCode la sala a obtener
     * @return el estado actual de la sala
     * @throws RoomNotFoundException si no existe ninguna sala con este código
     */
    public RoomState getRoom(String roomCode) {
        RoomState room = rooms.get(roomCode);
        if (room == null) {
            throw new RoomNotFoundException("Room not found: " + roomCode);
        }
        return room;
    }

    /**
     * Asocia la sesión WebSocket en vivo de un atleta con su sala y lo marca
     * como conectado, se llama cuando se establece una conexión WebSocket.
     */
    public void registerSession(String roomCode, String email, WebSocketSession session) {
        RoomState room = getRoom(roomCode);
        room.getSessions().put(email, session);
        AthleteState athlete = room.getAthletes().get(email);
        if (athlete != null) {
            athlete.setConnected(true);
        }
    }

    /** Elimina la sesión WebSocket de un atleta y lo marca como desconectado, se llama al cerrarse. */
    public void unregisterSession(String roomCode, String email) {
        RoomState room = getRoom(roomCode);
        room.getSessions().remove(email);
        AthleteState athlete = room.getAthletes().get(email);
        if (athlete != null) {
            athlete.setConnected(false);
        }
    }

    /** @return un código de sala aleatorio de 6 caracteres en mayúsculas */
    private String generateRoomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
