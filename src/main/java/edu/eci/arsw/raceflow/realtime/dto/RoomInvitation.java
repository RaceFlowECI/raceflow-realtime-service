package edu.eci.arsw.raceflow.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Una invitación pendiente para unirse a una sala, mantenida en memoria mientras la sala exista. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomInvitation {
    private String roomCode;
    private String fromEmail;
    private String fromName;
    private LocalDateTime sentAt;
}
