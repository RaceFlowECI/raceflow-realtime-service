package edu.eci.arsw.raceflow.realtime.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Inbound WebSocket message carrying an athlete's current GPS coordinates. */
@Data
@NoArgsConstructor
public class PositionMessage {
    private String type;
    private double latitude;
    private double longitude;
}
