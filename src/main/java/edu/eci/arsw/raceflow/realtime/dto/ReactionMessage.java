package edu.eci.arsw.raceflow.realtime.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Legacy inbound WebSocket message for emoji reactions (superseded by voice chat, kept for compatibility). */
@Data
@NoArgsConstructor
public class ReactionMessage {
    private String type;
    private String emoji;
}
