package edu.eci.arsw.raceflow.realtime.messaging;

import edu.eci.arsw.raceflow.realtime.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoomEventPublisherTest {

    @Test
    void publishesRoomActivatedEventToTheSharedExchange() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RoomEventPublisher publisher = new RoomEventPublisher(rabbitTemplate);

        publisher.publishRoomActivated("ABC123", "juan@raceflow.dev");

        ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EVENTS_EXCHANGE), eq("room.activated"), captor.capture());

        RoomEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("room.activated");
        assertThat(event.getRoomCode()).isEqualTo("ABC123");
        assertThat(event.getCreatedBy()).isEqualTo("juan@raceflow.dev");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void swallowsBrokerFailureInsteadOfPropagatingIt() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doThrow(new AmqpException("broker unreachable"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));
        RoomEventPublisher publisher = new RoomEventPublisher(rabbitTemplate);

        // best-effort publish -- a broker outage must not block room creation
        assertThatCode(() -> publisher.publishRoomActivated("ABC123", "juan@raceflow.dev"))
                .doesNotThrowAnyException();
    }
}
