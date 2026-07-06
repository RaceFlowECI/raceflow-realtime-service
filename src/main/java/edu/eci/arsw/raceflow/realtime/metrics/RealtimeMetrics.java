package edu.eci.arsw.raceflow.realtime.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RealtimeMetrics {

    private final Counter positionsReceived;
    private final Counter rankingUpdates;
    private final Counter reactionsSent;
    private final Timer rankingUpdateDuration;
    private final Timer redisWriteDuration;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

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

    public void recordPositionReceived() { positionsReceived.increment(); }
    public void recordPositionRejected(String reason, MeterRegistry registry) {
        registry.counter("raceflow.positions.rejected", "reason", reason).increment();
    }
    public void recordRankingUpdate() { rankingUpdates.increment(); }
    public void recordReactionSent() { reactionsSent.increment(); }
    public Timer getRankingUpdateDuration() { return rankingUpdateDuration; }
    public Timer getRedisWriteDuration() { return redisWriteDuration; }
    public void connectionOpened() { activeConnections.incrementAndGet(); }
    public void connectionClosed() { activeConnections.decrementAndGet(); }
}
