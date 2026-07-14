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

/** Endpoints REST para invitar amigos a una sala y gestionar las propias invitaciones pendientes. */
@RestController
public class InvitationController {

    private final InvitationService invitationService;
    private final RoomManager roomManager;
    private final JwtService jwtService;

    /**
     * @param invitationService almacén en memoria de invitaciones
     * @param roomManager       usado para resolver el nombre autoritativo de quien invita
     * @param jwtService        extrae el email de quien llama desde el token bearer
     */
    public InvitationController(InvitationService invitationService, RoomManager roomManager,
                                 JwtService jwtService) {
        this.invitationService = invitationService;
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

    /**
     * Invita a un atleta a la sala indicada, en nombre de quien llama.
     *
     * @param authorization token bearer de quien invita
     * @param roomCode      la sala a la que se invita
     * @param body          debe contener el {@code email} de la persona invitada
     * @return {@code 201 Created} en caso de éxito
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
     * @param authorization token bearer de quien llama
     * @return las invitaciones pendientes de quien llama
     */
    @GetMapping("/invitations")
    public ResponseEntity<List<RoomInvitation>> myInvitations(
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(invitationService.pendingFor(emailFromHeader(authorization)));
    }

    /**
     * Descarta una invitación pendiente de quien llama (al rechazarla, o
     * al limpiarla después de unirse a través de ella).
     *
     * @param authorization token bearer de quien llama
     * @param roomCode      la sala cuya invitación debe descartarse
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
     * @param authorization el valor crudo del header {@code Authorization}
     * @return el email de quien llama, extraído del JWT bearer
     */
    private String emailFromHeader(String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        return jwtService.extractEmail(token);
    }
}
