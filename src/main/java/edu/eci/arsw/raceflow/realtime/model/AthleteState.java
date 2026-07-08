package edu.eci.arsw.raceflow.realtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AthleteState {
    private String email;
    private String name;
    private double latitude;
    private double longitude;
    private double totalDistanceKm;
    private LocalDateTime lastUpdate;
    private boolean connected;
}
