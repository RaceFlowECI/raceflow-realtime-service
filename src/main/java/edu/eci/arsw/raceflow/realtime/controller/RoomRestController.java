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

@RestController
@RequestMapping("/rooms")
public class RoomRestController {

    private final RoomManager roomManager;
    private final JwtService jwtService;

    public RoomRestController(RoomManager roomManager, JwtService jwtService) {
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

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

    private String emailFromHeader(String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        return jwtService.extractEmail(token);
    }
}
