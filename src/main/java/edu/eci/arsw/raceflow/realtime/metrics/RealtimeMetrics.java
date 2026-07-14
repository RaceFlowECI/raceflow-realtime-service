package edu.eci.arsw.raceflow.realtime.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

/** Contadores/temporizadores/gauges de Micrometer para realtime-service, expuestos en {@code /actuator/prometheus}. */
@Component
public class RealtimeMetrics {

    private final Counter positionsReceived;
    private final Counter rankingUpdates;
    private final Counter reactionsSent;
    private final Timer rankingUpdateDuration;
    private final Timer redisWriteDuration;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    /**
     * @param registry el registro de Micrometer al cual asociar estas métricas
     */
    public RealtimeMetrics(MeterRegistry registry) {
        this.positionsReceived = Counter.builder("raceflow.positions.received")
                .description("Total GPS positions received")
                .register(registry);

        this.rankingUpdates = Counter.builder("raceflow.ranking.updates")
                .description("Total ranking updates computed")
                .register(registry);

        this.reactionsSent = Counter.builder("raceflow.reactions.sent")
                .description("Total reactions sent to clients")
                .register(registry);

        // SLO p99 <= 1s — critical metric
        this.rankingUpdateDuration = Timer.builder("raceflow.ranking.update.duration")
                .description("Time to compute and broadcast ranking update")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.redisWriteDuration = Timer.builder("raceflow.redis.write.duration")
                .description("Time to write ranking state to Redis")
                .register(registry);

        Gauge.builder("raceflow.websocket.connections.active", activeConnections, AtomicInteger::get)
                .description("Currently active WebSocket connections")
                .register(registry);

        // Pre-register rejection reason tags
        Counter.builder("raceflow.positions.rejected").tag("reason", "invalid_jump").register(registry);
        Counter.builder("raceflow.positions.rejected").tag("reason", "out_of_bounds").register(registry);
        Counter.builder("raceflow.positions.rejected").tag("reason", "malformed").register(registry);
    }

    /** Incrementa el contador total de posiciones recibidas. */
    public void recordPositionReceived() { positionsReceived.increment(); }
    /**
     * Incrementa el contador de posiciones rechazadas, etiquetado por motivo.
     *
     * @param reason   uno de {@code invalid_jump}, {@code out_of_bounds}, {@code malformed}
     * @param registry el registro del cual resolver el contador etiquetado
     */
    public void recordPositionRejected(String reason, MeterRegistry registry) {
        registry.counter("raceflow.positions.rejected", "reason", reason).increment();
    }
    /** Incrementa el contador total de actualizaciones de ranking. */
    public void recordRankingUpdate() { rankingUpdates.increment(); }
    /** Incrementa el contador total de reacciones enviadas. */
    public void recordReactionSent() { reactionsSent.increment(); }
    /** @return el temporizador usado para medir la latencia de recálculo del ranking (SLO p99 &lt;= 1s) */
    public Timer getRankingUpdateDuration() { return rankingUpdateDuration; }
    /** @return el temporizador usado para medir la latencia de escritura en Redis */
    public Timer getRedisWriteDuration() { return redisWriteDuration; }
    /** Incrementa el gauge de conexiones WebSocket activas. */
    public void connectionOpened() { activeConnections.incrementAndGet(); }
    /** Decrementa el gauge de conexiones WebSocket activas. */
    public void connectionClosed() { activeConnections.decrementAndGet(); }
}
