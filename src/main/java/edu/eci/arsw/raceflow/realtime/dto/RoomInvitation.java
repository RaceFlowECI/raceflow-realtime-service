package edu.eci.arsw.raceflow.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A pending invitation to join a room, kept in memory for as long as the room exists. */
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
