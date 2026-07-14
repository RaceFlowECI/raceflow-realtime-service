package edu.eci.arsw.raceflow.realtime.strategy;

import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RankingEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Default {@link RankingStrategy}: connected athletes first, then ordered by
 * total distance covered (descending), with the most-recently-updated athlete
 * winning ties.
 */
@Component
public class DistanceRankingStrategy implements RankingStrategy {

    @Override
    public List<RankingEntry> rank(Collection<AthleteState> athletes) {
        Comparator<AthleteState> byConnectedFirst = Comparator.comparing(AthleteState::isConnected).reversed();
        Comparator<AthleteState> byDistanceDesc = Comparator.comparingDouble(AthleteState::getTotalDistanceKm).reversed();
        Comparator<AthleteState> byLastUpdateAsc = Comparator.comparing(
                AthleteState::getLastUpdate,
                Comparator.nullsLast(Comparator.naturalOrder())
        );

        List<AthleteState> sorted = new ArrayList<>(athletes);
        sorted.sort(byConnectedFirst.thenComparing(byDistanceDesc).thenComparing(byLastUpdateAsc));

        List<RankingEntry> ranking = new ArrayList<>(sorted.size());
        int rank = 1;
        for (AthleteState athlete : sorted) {
            ranking.add(RankingEntry.builder()
                    .rank(rank++)
                    .email(athlete.getEmail())
                    .name(athlete.getName())
                    .latitude(athlete.getLatitude())
                    .longitude(athlete.getLongitude())
                    .distanceKm(athlete.getTotalDistanceKm())
                    .connected(athlete.isConnected())
                    .build());
        }
        return ranking;
    }
}
