package edu.eci.arsw.raceflow.realtime.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeMetricsTest {

    private MeterRegistry registry;
    private RealtimeMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new RealtimeMetrics(registry);
    }

    @Test
    void recordPositionReceivedIncrementsCounter() {
        metrics.recordPositionReceived();
        metrics.recordPositionReceived();

        assertThat(registry.get("raceflow.positions.received").counter().count())
                .isEqualTo(2.0);
    }

    @Test
    void recordPositionRejectedIncrementsTaggedCounter() {
        metrics.recordPositionRejected("invalid_jump", registry);
        metrics.recordPositionRejected("invalid_jump", registry);
        metrics.recordPositionRejected("out_of_bounds", registry);

        assertThat(registry.get("raceflow.positions.rejected")
                .tag("reason", "invalid_jump").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("raceflow.positions.rejected")
                .tag("reason", "out_of_bounds").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordRankingUpdateIncrementsCounter() {
        metrics.recordRankingUpdate();

        assertThat(registry.get("raceflow.ranking.updates").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordReactionSentIncrementsCounter() {
        metrics.recordReactionSent();
        metrics.recordReactionSent();
        metrics.recordReactionSent();

        assertThat(registry.get("raceflow.reactions.sent").counter().count())
                .isEqualTo(3.0);
    }

    @Test
    void rankingUpdateDurationRecordsTimings() {
        metrics.getRankingUpdateDuration().record(java.time.Duration.ofMillis(5));

        assertThat(registry.get("raceflow.ranking.update.duration").timer().count())
                .isEqualTo(1);
    }

    @Test
    void redisWriteDurationRecordsTimings() {
        metrics.getRedisWriteDuration().record(java.time.Duration.ofMillis(3));

        assertThat(registry.get("raceflow.redis.write.duration").timer().count())
                .isEqualTo(1);
    }

    @Test
    void activeConnectionsGaugeTracksOpenAndClose() {
        metrics.connectionOpened();
        metrics.connectionOpened();
        metrics.connectionClosed();

        assertThat(registry.get("raceflow.websocket.connections.active").gauge().value())
                .isEqualTo(1.0);
    }
}
