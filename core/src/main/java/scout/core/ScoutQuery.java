package scout.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A typed scouting search. Built by the UI or by the assistant, validated by {@link #validate}, run by SQL or in memory. */
public record ScoutQuery(Set<String> positions, Integer minAge, Integer maxAge, String foot, Set<Integer> leagueIds,
                         Integer minMinutes, Metric sortBy, Integer limit) {

    public static final Set<String> POSITIONS = Set.of("GK", "CB", "FB", "DM", "CM", "AM", "W", "ST");

    public ScoutQuery {
        positions = positions == null ? Set.of() : Set.copyOf(positions);
        leagueIds = leagueIds == null ? Set.of() : Set.copyOf(leagueIds);
        sortBy = sortBy == null ? Metric.PROGRESSION : sortBy;
    }

    public int effectiveLimit(Settings.Query s) {
        return limit == null ? s.defaultLimit() : limit;
    }

    public int effectiveMinMinutes(Settings.Query s) {
        return minMinutes == null ? s.minMinutesDefault() : minMinutes;
    }

    /** Problems a person must fix; an empty list means the query can run. */
    public List<String> validate(Catalog catalog, Settings.Query s) {
        List<String> out = new ArrayList<>();
        for (String p : positions) if (!POSITIONS.contains(p)) out.add("unknown position " + p);
        if (minAge != null && (minAge < 15 || minAge > s.maxAge())) out.add("minimum age " + minAge + " is outside 15 to " + s.maxAge());
        if (maxAge != null && (maxAge < 15 || maxAge > s.maxAge())) out.add("maximum age " + maxAge + " is outside 15 to " + s.maxAge());
        if (minAge != null && maxAge != null && minAge > maxAge) out.add("minimum age is above maximum age");
        if (foot != null && !Set.of("left", "right").contains(foot)) out.add("foot must be left or right");
        for (int id : leagueIds) if (catalog.league(id) == null) out.add("no league with id " + id);
        if (minMinutes != null && (minMinutes < 0 || minMinutes > 6000)) out.add("minimum minutes must be between 0 and 6000");
        if (limit != null && (limit < 1 || limit > s.maxLimit())) out.add("limit must be between 1 and " + s.maxLimit());
        return out;
    }
}
