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
 * Room invitations, kept in memory on purpose: an invitation is exactly as
 * ephemeral as the room it points to (both die with the process). Persistent
 * relationships (friendships) live in auth-service instead.
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
     * Invites an athlete to a room. Silently ignored if the invitee already has a
     * pending invitation for this room.
     *
     * @param roomCode  the room to invite to
     * @param fromEmail the inviter's email
     * @param fromName  the inviter's display name
     * @param toEmail   the invitee's email
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
     * @param email    the invitee's email
     * @param roomCode the room to discard the invitation for
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
