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
     * @param athletes los atletas actuales de la sala
     * @return los atletas ordenados en un ranking, de mejor a peor
     */
    List<RankingEntry> rank(Collection<AthleteState> athletes);
}
