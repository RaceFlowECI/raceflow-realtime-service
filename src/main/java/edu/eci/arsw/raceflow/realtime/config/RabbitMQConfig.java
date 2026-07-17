package edu.eci.arsw.raceflow.realtime.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the shared topic exchange domain events are published to. Consumers
 * (currently metrics-service) declare their own queues and bindings against
 * this same exchange name/routing-key scheme.
 */
@Configuration
public class RabbitMQConfig {

    /** Name of the topic exchange all RaceFlow domain events are published to. */
    public static final String EVENTS_EXCHANGE = "raceflow.events";

    /** @return the topic exchange, durable so it survives a broker restart */
    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    /** @return a JSON message converter so event payloads are readable across services */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
