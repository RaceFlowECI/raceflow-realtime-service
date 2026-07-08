package edu.eci.arsw.raceflow.realtime.strategy;

import edu.eci.arsw.raceflow.realtime.model.AthleteState;
import edu.eci.arsw.raceflow.realtime.model.RankingEntry;

import java.util.Collection;
import java.util.List;

public interface RankingStrategy {
    List<RankingEntry> rank(Collection<AthleteState> athletes);
}
