package edu.eci.arsw.raceflow.realtime.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReactionMessage {
    private String type;
    private String emoji;
}
