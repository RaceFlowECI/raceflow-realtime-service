package edu.eci.arsw.raceflow.realtime.dto;

import edu.eci.arsw.raceflow.realtime.model.RankingEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Outbound WebSocket message broadcasting the current ranking of a room. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStateMessage {
    private String type;
    private String roomCode;
    private List<RankingEntry> ranking;
    private LocalDateTime timestamp;
}
