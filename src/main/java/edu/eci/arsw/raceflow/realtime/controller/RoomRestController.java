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

/** Endpoints REST para el ciclo de vida de la sala: crear, unirse, y consultar estado. */
@RestController
@RequestMapping("/rooms")
public class RoomRestController {

    private final RoomManager roomManager;
    private final JwtService jwtService;

    /**
     * @param roomManager almacén en memoria de salas activas
     * @param jwtService  extrae el email de quien llama desde el token bearer
     */
    public RoomRestController(RoomManager roomManager, JwtService jwtService) {
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

    /**
     * Crea una nueva sala con quien llama como su primer atleta.
     *
     * @param authorization token bearer del creador
     * @param body          debe contener un {@code name} suministrado por el cliente (sujeto a override por gRPC)
     * @return {@code 201 Created} con el código de sala generado
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
     * Se une a una sala existente.
     *
     * @param authorization token bearer del atleta que se une
     * @param body          debe contener {@code roomCode} y un {@code name} suministrado por el cliente
     * @return {@code 200 OK} con el código de sala y el conteo actual de atletas
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
     * Devuelve el estado actual de una sala (sus atletas y sus posiciones).
     *
     * @param authorization token bearer de quien llama (debe ser válido, pero cualquier usuario autenticado puede leer el estado)
     * @param roomCode      la sala a consultar
     * @return el código de sala y la lista actual de atletas
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
     * @param authorization el valor crudo del header {@code Authorization}
     * @return el email de quien llama, extraído del JWT bearer
     */
    private String emailFromHeader(String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        return jwtService.extractEmail(token);
    }
}
