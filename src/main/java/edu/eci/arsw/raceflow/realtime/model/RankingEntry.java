package edu.eci.arsw.raceflow.realtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of a room's computed ranking, as broadcast to clients. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingEntry {
    private int rank;
    private String email;
    private String name;
    private double latitude;
    private double longitude;
    private double distanceKm;
    private boolean connected;
}
