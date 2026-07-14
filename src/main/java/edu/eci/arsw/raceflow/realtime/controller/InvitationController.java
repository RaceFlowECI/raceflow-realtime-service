package edu.eci.arsw.raceflow.realtime.controller;

import edu.eci.arsw.raceflow.realtime.dto.RoomInvitation;
import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.service.InvitationService;
import edu.eci.arsw.raceflow.realtime.service.JwtService;
import edu.eci.arsw.raceflow.realtime.service.RoomManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** REST endpoints for inviting friends to a room and managing one's own pending invitations. */
@RestController
public class InvitationController {

    private final InvitationService invitationService;
    private final RoomManager roomManager;
    private final JwtService jwtService;

    /**
     * @param invitationService in-memory invitation store
     * @param roomManager       used to resolve the inviter's authoritative name
     * @param jwtService        extracts the caller's email from the bearer token
     */
    public InvitationController(InvitationService invitationService, RoomManager roomManager,
                                 JwtService jwtService) {
        this.invitationService = invitationService;
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

    /**
     * Invites an athlete to the given room, on behalf of the caller.
     *
     * @param authorization bearer token of the inviter
     * @param roomCode      the room to invite to
     * @param body          must contain the invitee's {@code email}
     * @return {@code 201 Created} on success
     */
    @PostMapping("/rooms/{roomCode}/invite")
    public ResponseEntity<Void> invite(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String roomCode,
            @RequestBody Map<String, String> body) {

        String fromEmail = emailFromHeader(authorization);
        String toEmail = body.get("email");

        AthleteState sender = roomManager.getRoom(roomCode).getAthletes().get(fromEmail);
        String fromName = sender != null ? sender.getName() : fromEmail;

        invitationService.invite(roomCode, fromEmail, fromName, toEmail);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * @param authorization bearer token of the caller
     * @return the caller's pending invitations
     */
    @GetMapping("/invitations")
    public ResponseEntity<List<RoomInvitation>> myInvitations(
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(invitationService.pendingFor(emailFromHeader(authorization)));
    }

    /**
     * Discards a pending invitation for the caller (declining it, or
     * clearing it after joining through it).
     *
     * @param authorization bearer token of the caller
     * @param roomCode      the room whose invitation should be discarded
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/invitations/{roomCode}")
    public ResponseEntity<Void> decline(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String roomCode) {
        invitationService.discard(emailFromHeader(authorization), roomCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * @param authorization the raw {@code Authorization} header value
     * @return the caller's email, extracted from the bearer JWT
     */
    private String emailFromHeader(String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        return jwtService.extractEmail(token);
    }
}
