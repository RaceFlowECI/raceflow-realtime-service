package edu.eci.arsw.raceflow.realtime.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

/** Micrometer counters/timers/gauges for realtime-service, exposed at {@code /actuator/prometheus}. */
@Component
public class RealtimeMetrics {

    private final Counter positionsReceived;
    private final Counter rankingUpdates;
    private final Counter reactionsSent;
    private final Timer rankingUpdateDuration;
    private final Timer redisWriteDuration;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    /**
     * @param registry the Micrometer registry to bind these meters to
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

    /** Increments the total received-positions counter. */
    public void recordPositionReceived() { positionsReceived.increment(); }
    /**
     * Increments the rejected-positions counter, tagged by reason.
     *
     * @param reason   one of {@code invalid_jump}, {@code out_of_bounds}, {@code malformed}
     * @param registry the registry to resolve the tagged counter from
     */
    public void recordPositionRejected(String reason, MeterRegistry registry) {
        registry.counter("raceflow.positions.rejected", "reason", reason).increment();
    }
    /** Increments the total ranking-updates counter. */
    public void recordRankingUpdate() { rankingUpdates.increment(); }
    /** Increments the total reactions-sent counter. */
    public void recordReactionSent() { reactionsSent.increment(); }
    /** @return the timer used to measure ranking recomputation latency (SLO p99 &lt;= 1s) */
    public Timer getRankingUpdateDuration() { return rankingUpdateDuration; }
    /** @return the timer used to measure Redis write latency */
    public Timer getRedisWriteDuration() { return redisWriteDuration; }
    /** Increments the active-WebSocket-connections gauge. */
    public void connectionOpened() { activeConnections.incrementAndGet(); }
    /** Decrements the active-WebSocket-connections gauge. */
    public void connectionClosed() { activeConnections.decrementAndGet(); }
}
