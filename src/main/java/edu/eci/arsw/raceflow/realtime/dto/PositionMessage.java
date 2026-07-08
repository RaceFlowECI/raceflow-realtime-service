package edu.eci.arsw.raceflow.realtime.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PositionMessage {
    private String type;
    private double latitude;
    private double longitude;
}
