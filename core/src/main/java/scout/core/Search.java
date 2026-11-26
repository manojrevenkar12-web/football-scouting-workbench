package scout.core;

import java.util.List;

/** The same search as {@link SqlCompiler}, over ratings in memory. Tests require both to return the same players. */
public final class Search {
    private Search() {}

    public static List<Ratings.Rating> run(List<Ratings.Rating> ratings, ScoutQuery q, Settings.Query s) {
        int minMinutes = q.effectiveMinMinutes(s);
        return ratings.stream()
            .filter(r -> r.minutes() >= minMinutes)
            .filter(r -> q.positions().isEmpty() || q.positions().contains(r.position()))
            .filter(r -> q.minAge() == null || r.age() >= q.minAge())
            .filter(r -> q.maxAge() == null || r.age() <= q.maxAge())
            .filter(r -> q.foot() == null || q.foot().equals(r.foot()))
            .filter(r -> q.leagueIds().isEmpty() || q.leagueIds().contains(r.leagueId()))
            .sorted(q.sortBy().order())
            .limit(q.effectiveLimit(s))
            .toList();
    }
}
