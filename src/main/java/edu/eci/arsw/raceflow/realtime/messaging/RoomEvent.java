package edu.eci.arsw.raceflow.realtime.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Domain event published to RabbitMQ when a room's lifecycle changes. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomEvent {
    private String eventType;
    private String roomCode;
    private String createdBy;
    private Instant timestamp;
}
