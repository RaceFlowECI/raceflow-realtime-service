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

@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);
    private static final Duration RANKING_TTL = Duration.ofHours(1);

    private final RankingStrategy rankingStrategy;
    private final RealtimeMetrics metrics;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RankingService(RankingStrategy rankingStrategy,
                           RealtimeMetrics metrics,
                           RedisTemplate<String, String> redisTemplate,
                           ObjectMapper objectMapper) {
        this.rankingStrategy = rankingStrategy;
        this.metrics = metrics;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RankingEntry> computeAndStore(RoomState room) {
        return metrics.getRankingUpdateDuration().record(() -> {
            List<RankingEntry> ranking = rankingStrategy.rank(room.getAthletes().values());
            storeInRedis(room.getRoomCode(), ranking);
            return ranking;
        });
    }

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

    public double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
