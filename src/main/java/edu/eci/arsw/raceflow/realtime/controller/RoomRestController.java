package edu.eci.arsw.raceflow.realtime.controller;

import edu.eci.arsw.raceflow.realtime.dto.CreateRoomResponse;
import edu.eci.arsw.raceflow.realtime.dto.JoinRoomResponse;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import edu.eci.arsw.raceflow.realtime.service.JwtService;
import edu.eci.arsw.raceflow.realtime.service.RoomManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** REST endpoints for the room lifecycle: create, join, and query state. */
@RestController
@RequestMapping("/rooms")
public class RoomRestController {

    private final RoomManager roomManager;
    private final JwtService jwtService;

    /**
     * @param roomManager in-memory store of active rooms
     * @param jwtService  extracts the caller's email from the bearer token
     */
    public RoomRestController(RoomManager roomManager, JwtService jwtService) {
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

    /**
     * Creates a new room with the caller as its first athlete.
     *
     * @param authorization bearer token of the creator
     * @param body          must contain a client-supplied {@code name} (subject to gRPC override)
     * @return {@code 201 Created} with the generated room code
     */
    @PostMapping("/create")
    public ResponseEntity<CreateRoomResponse> create(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> body) {

        String email = emailFromHeader(authorization);
        String name = body.get("name");

        String roomCode = roomManager.createRoom(email, name);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreateRoomResponse.builder().roomCode(roomCode).createdBy(email).build());
    }

    /**
     * Joins an existing room.
     *
     * @param authorization bearer token of the joining athlete
     * @param body          must contain {@code roomCode} and a client-supplied {@code name}
     * @return {@code 200 OK} with the room code and current athlete count
     */
    @PostMapping("/join")
    public ResponseEntity<JoinRoomResponse> join(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, String> body) {

        String email = emailFromHeader(authorization);
        String roomCode = body.get("roomCode");
        String name = body.get("name");

        RoomState room = roomManager.joinRoom(roomCode, email, name);

        return ResponseEntity.ok(JoinRoomResponse.builder()
                .roomCode(roomCode)
                .athleteCount(room.getAthletes().size())
                .build());
    }

    /**
     * Returns the current state of a room (its athletes and their positions).
     *
     * @param authorization bearer token of the caller (must be valid, but any authenticated user may read state)
     * @param roomCode      the room to query
     * @return the room code and the current list of athletes
     */
    @GetMapping("/{roomCode}/state")
    public ResponseEntity<Map<String, Object>> state(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String roomCode) {

        emailFromHeader(authorization);
        RoomState room = roomManager.getRoom(roomCode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomCode", room.getRoomCode());
        body.put("athletes", room.getAthletes().values());

        return ResponseEntity.ok(body);
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
