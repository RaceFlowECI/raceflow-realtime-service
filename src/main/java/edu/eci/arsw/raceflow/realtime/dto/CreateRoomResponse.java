package edu.eci.arsw.raceflow.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Respuesta devuelta tras crear una sala exitosamente. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomResponse {
    private String roomCode;
    private String createdBy;
}
