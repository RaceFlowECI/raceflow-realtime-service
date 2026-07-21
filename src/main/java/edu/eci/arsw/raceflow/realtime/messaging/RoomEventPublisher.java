package edu.eci.arsw.raceflow.realtime.messaging;

import edu.eci.arsw.raceflow.realtime.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publishes room lifecycle events to the shared {@code raceflow.events} topic
 * exchange. Currently only {@code room.activated} is published (on room
 * creation); other event types metrics-service already listens for
 * ({@code room.finished}, {@code session.finished}) are not yet emitted by
 * any service.
 */
@Component
public class RoomEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RoomEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RoomEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes a {@code room.activated} event. Best-effort: a broker failure
     * is logged and swallowed rather than propagated, so a RabbitMQ outage
     * doesn't block room creation.
     *
     * @param roomCode  the newly created room's code
     * @param createdBy the creator's email
     */
    public void publishRoomActivated(String roomCode, String createdBy) {
        RoomEvent event = RoomEvent.builder()
                .eventType("room.activated")
                .roomCode(roomCode)
                .createdBy(createdBy)
                .timestamp(Instant.now())
                .build();
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, "room.activated", event);
        } catch (Exception e) {
            log.warn("Failed to publish room.activated event for room {}: {}", roomCode, e.getMessage());
        }
    }
}
