package edu.eci.arsw.raceflow.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.arsw.raceflow.realtime.metrics.RealtimeMetrics;
import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RankingEntry;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import edu.eci.arsw.raceflow.realtime.strategy.DistanceRankingStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RealtimeMetrics metrics = new RealtimeMetrics(new SimpleMeterRegistry());
        rankingService = new RankingService(new DistanceRankingStrategy(), metrics, redisTemplate, new ObjectMapper());
    }

    @Test
    void computeAndStoreRanksAthletesAndPersistsToRedis() {
        RoomState room = new RoomState("ABC123", "juan@raceflow.dev");
        room.getAthletes().put("juan@raceflow.dev", AthleteState.builder()
                .email("juan@raceflow.dev").name("Juan").totalDistanceKm(5.0).connected(true).build());
        room.getAthletes().put("ana@raceflow.dev", AthleteState.builder()
                .email("ana@raceflow.dev").name("Ana").totalDistanceKm(2.0).connected(true).build());

        List<RankingEntry> ranking = rankingService.computeAndStore(room);

        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).getEmail()).isEqualTo("juan@raceflow.dev");

        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void computeAndStoreStoresUnderRoomCodePrefixedKey() {
        RoomState room = new RoomState("XYZ999", "ana@raceflow.dev");
        room.getAthletes().put("ana@raceflow.dev", AthleteState.builder()
                .email("ana@raceflow.dev").name("Ana").totalDistanceKm(1.0).connected(true).build());

        rankingService.computeAndStore(room);

        verify(valueOperations).set(eq("ranking:XYZ999"), anyString(), any(Duration.class));
    }

    @Test
    void haversineKmReturnsZeroForSamePoint() {
        assertThat(rankingService.haversineKm(4.65, -74.05, 4.65, -74.05)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void haversineKmMatchesKnownDistanceBetweenBogotaAndMedellin() {
        // Approx. straight-line distance Bogota <-> Medellin ~ 240km
        double distance = rankingService.haversineKm(4.7110, -74.0721, 6.2442, -75.5812);
        assertThat(distance).isBetween(230.0, 250.0);
    }
}
