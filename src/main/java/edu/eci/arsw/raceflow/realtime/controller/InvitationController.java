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

@RestController
public class InvitationController {

    private final InvitationService invitationService;
    private final RoomManager roomManager;
    private final JwtService jwtService;

    public InvitationController(InvitationService invitationService, RoomManager roomManager,
                                 JwtService jwtService) {
        this.invitationService = invitationService;
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

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

    @GetMapping("/invitations")
    public ResponseEntity<List<RoomInvitation>> myInvitations(
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(invitationService.pendingFor(emailFromHeader(authorization)));
    }

    @DeleteMapping("/invitations/{roomCode}")
    public ResponseEntity<Void> decline(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String roomCode) {
        invitationService.discard(emailFromHeader(authorization), roomCode);
        return ResponseEntity.noContent().build();
    }

    private String emailFromHeader(String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        return jwtService.extractEmail(token);
    }
}
