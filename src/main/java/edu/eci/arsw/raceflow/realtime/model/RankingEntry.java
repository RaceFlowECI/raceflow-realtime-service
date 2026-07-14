package edu.eci.arsw.raceflow.realtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Una fila del ranking calculado de una sala, tal como se transmite a los clientes. */
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
