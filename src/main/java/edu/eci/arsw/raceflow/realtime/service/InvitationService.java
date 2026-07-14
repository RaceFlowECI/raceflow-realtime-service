package edu.eci.arsw.raceflow.realtime.service;

import edu.eci.arsw.raceflow.realtime.dto.RoomInvitation;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Invitaciones a salas, mantenidas en memoria a propósito: una invitación es exactamente
 * tan efímera como la sala a la que apunta (ambas mueren con el proceso). Las relaciones
 * persistentes (amistades) viven en auth-service.
 */
@Service
public class InvitationService {

    private final Map<String, List<RoomInvitation>> byInvitee = new ConcurrentHashMap<>();
    private final RoomManager roomManager;

    /**
     * @param roomManager used to validate that a room exists before inviting, and to
     *                    detect rooms that have since died (orphaned invitations)
     */
    public InvitationService(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    /**
     * Invita a un atleta a una sala. Se ignora silenciosamente si la persona invitada ya tiene una
     * invitación pendiente para esta sala.
     *
     * @param roomCode  la sala a la que se invita
     * @param fromEmail el email de quien invita
     * @param fromName  el nombre visible de quien invita
     * @param toEmail   el email de la persona invitada
     */
    public void invite(String roomCode, String fromEmail, String fromName, String toEmail) {
        RoomState room = roomManager.getRoom(roomCode); // valida que la sala exista
        List<RoomInvitation> list = byInvitee.computeIfAbsent(
                toEmail.toLowerCase(), k -> new CopyOnWriteArrayList<>());

        boolean duplicated = list.stream().anyMatch(i -> i.getRoomCode().equals(room.getRoomCode()));
        if (!duplicated) {
            list.add(RoomInvitation.builder()
                    .roomCode(room.getRoomCode())
                    .fromEmail(fromEmail)
                    .fromName(fromName)
                    .sentAt(LocalDateTime.now())
                    .build());
        }
    }

    /** Devuelve solo invitaciones cuya sala sigue viva; las huérfanas se descartan. */
    public List<RoomInvitation> pendingFor(String email) {
        List<RoomInvitation> list = byInvitee.getOrDefault(email.toLowerCase(), List.of());
        List<RoomInvitation> alive = list.stream().filter(i -> roomExists(i.getRoomCode())).toList();
        if (alive.size() != list.size()) {
            byInvitee.put(email.toLowerCase(), new CopyOnWriteArrayList<>(alive));
        }
        return alive;
    }

    /**
     * Removes a pending invitation, e.g. after the invitee accepts or declines it.
     *
     * @param email    el email de la persona invitada
     * @param roomCode la sala para la cual descartar la invitación
     */
    public void discard(String email, String roomCode) {
        byInvitee.getOrDefault(email.toLowerCase(), List.of())
                .removeIf(i -> i.getRoomCode().equals(roomCode));
    }

    private boolean roomExists(String roomCode) {
        try {
            roomManager.getRoom(roomCode);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
