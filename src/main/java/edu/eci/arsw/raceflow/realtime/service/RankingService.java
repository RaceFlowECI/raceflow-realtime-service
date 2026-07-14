package edu.eci.arsw.raceflow.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.arsw.raceflow.realtime.metrics.RealtimeMetrics;
import edu.eci.arsw.raceflow.realtime.model.RankingEntry;
import edu.eci.arsw.raceflow.realtime.model.RoomState;
import edu.eci.arsw.raceflow.realtime.strategy.RankingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Computes a room's ranking via the pluggable {@link RankingStrategy} and caches
 * the result in Redis (1h TTL) so it can be served even if this instance restarts
 * mid-session. Also times the computation against the service's p99 &lt;= 1s SLO.
 */
@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);
    private static final Duration RANKING_TTL = Duration.ofHours(1);

    private final RankingStrategy rankingStrategy;
    private final RealtimeMetrics metrics;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param rankingStrategy the algorithm used to order athletes (distance-based by default)
     * @param metrics         used to time ranking computation and Redis writes
     * @param redisTemplate   used to cache the computed ranking
     * @param objectMapper    used to serialize the ranking to JSON before caching
     */
    public RankingService(RankingStrategy rankingStrategy,
                           RealtimeMetrics metrics,
                           RedisTemplate<String, String> redisTemplate,
                           ObjectMapper objectMapper) {
        this.rankingStrategy = rankingStrategy;
        this.metrics = metrics;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Ranks a room's current athletes and stores the result in Redis, timed
     * against the ranking-update-duration SLO.
     *
     * @param room the room to rank
     * @return the computed ranking, ordered best-first
     */
    public List<RankingEntry> computeAndStore(RoomState room) {
        return metrics.getRankingUpdateDuration().record(() -> {
            List<RankingEntry> ranking = rankingStrategy.rank(room.getAthletes().values());
            storeInRedis(room.getRoomCode(), ranking);
            return ranking;
        });
    }

    /** Best-effort cache write; a Redis failure is logged and swallowed, not propagated. */
    private void storeInRedis(String roomCode, List<RankingEntry> ranking) {
        long start = System.nanoTime();
        try {
            String json = objectMapper.writeValueAsString(ranking);
            redisTemplate.opsForValue().set("ranking:" + roomCode, json, RANKING_TTL);
        } catch (Exception e) {
            log.warn("Failed to store ranking in Redis for room {}: {}", roomCode, e.getMessage());
        } finally {
            metrics.getRedisWriteDuration().record(Duration.ofNanos(System.nanoTime() - start));
        }
    }

    /**
     * Great-circle distance between two GPS points using the haversine formula.
     * Used to accumulate an athlete's total distance as new positions arrive.
     *
     * @return the distance in kilometers
     */
    public double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
