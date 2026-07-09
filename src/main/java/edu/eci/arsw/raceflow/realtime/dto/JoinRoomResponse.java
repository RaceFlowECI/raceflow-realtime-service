package edu.eci.arsw.raceflow.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomResponse {
    private String roomCode;
    private int athleteCount;
}
