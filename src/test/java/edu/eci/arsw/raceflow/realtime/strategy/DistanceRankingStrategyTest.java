package edu.eci.arsw.raceflow.realtime.strategy;

import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RankingEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceRankingStrategyTest {

    private final DistanceRankingStrategy strategy = new DistanceRankingStrategy();

    @Test
    void ranksConnectedAthletesByDistanceDescending() {
        AthleteState leader = athlete("leader@x.com", "Leader", 5.0, true, LocalDateTime.now());
        AthleteState second = athlete("second@x.com", "Second", 3.0, true, LocalDateTime.now());

        List<RankingEntry> ranking = strategy.rank(List.of(second, leader));

        assertThat(ranking).extracting(RankingEntry::getEmail)
                .containsExactly("leader@x.com", "second@x.com");
        assertThat(ranking.get(0).getRank()).isEqualTo(1);
        assertThat(ranking.get(1).getRank()).isEqualTo(2);
    }

    @Test
    void breaksTiesByLastUpdateAscending() {
        LocalDateTime earlier = LocalDateTime.now().minusMinutes(5);
        LocalDateTime later = LocalDateTime.now();

        AthleteState first = athlete("first@x.com", "First", 2.0, true, earlier);
        AthleteState secondSameDistance = athlete("second@x.com", "Second", 2.0, true, later);

        List<RankingEntry> ranking = strategy.rank(List.of(secondSameDistance, first));

        assertThat(ranking).extracting(RankingEntry::getEmail)
                .containsExactly("first@x.com", "second@x.com");
    }

    @Test
    void disconnectedAthletesAreRankedLast() {
        AthleteState disconnectedLeader = athlete("ghost@x.com", "Ghost", 100.0, false, LocalDateTime.now());
        AthleteState connectedTrailing = athlete("runner@x.com", "Runner", 1.0, true, LocalDateTime.now());

        List<RankingEntry> ranking = strategy.rank(List.of(disconnectedLeader, connectedTrailing));

        assertThat(ranking).extracting(RankingEntry::getEmail)
                .containsExactly("runner@x.com", "ghost@x.com");
        assertThat(ranking.get(1).isConnected()).isFalse();
    }

    @Test
    void emptyCollectionProducesEmptyRanking() {
        assertThat(strategy.rank(List.of())).isEmpty();
    }

    private AthleteState athlete(String email, String name, double distanceKm, boolean connected, LocalDateTime lastUpdate) {
        return AthleteState.builder()
                .email(email)
                .name(name)
                .latitude(4.0)
                .longitude(-74.0)
                .totalDistanceKm(distanceKm)
                .lastUpdate(lastUpdate)
                .connected(connected)
                .build();
    }
}
