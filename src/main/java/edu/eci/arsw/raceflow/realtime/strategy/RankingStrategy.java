package edu.eci.arsw.raceflow.realtime.strategy;

import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RankingEntry;

import java.util.Collection;
import java.util.List;

/**
 * Strategy interface for ordering a room's athletes into a ranking. Allows the
 * ranking algorithm (currently distance-based) to be swapped without touching
 * the callers that consume the result.
 */
public interface RankingStrategy {
    /**
     * @param athletes the room's current athletes
     * @return the athletes ordered into a ranking, best-first
     */
    List<RankingEntry> rank(Collection<AthleteState> athletes);
}
