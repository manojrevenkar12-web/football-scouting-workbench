package scout.core;

import java.util.Comparator;

/** What a scout can sort by. Each has one column in the player_ratings view; nothing else can be sorted on. */
public enum Metric {
    PROGRESSION("progression", "progression"),
    PROGRESSION_PER90_RAW("progression_per90_raw", "progressionPer90Raw"),
    XG_PER90("xg_per90", "xgPer90"),
    GOALS("goals", "goals"),
    DEFENSIVE_PER90("defensive_per90", "defensivePer90"),
    MINUTES("minutes", "minutes");

    public final String column;
    public final String field;

    Metric(String column, String field) {
        this.column = column;
        this.field = field;
    }

    public double of(Ratings.Rating r) {
        return switch (this) {
            case PROGRESSION -> r.progression();
            case PROGRESSION_PER90_RAW -> r.progressionPer90Raw();
            case XG_PER90 -> r.xgPer90();
            case GOALS -> r.goals();
            case DEFENSIVE_PER90 -> r.defensivePer90();
            case MINUTES -> r.minutes();
        };
    }

    /** Highest first; ties broken by player id so every executor returns the same order. */
    public Comparator<Ratings.Rating> order() {
        return Comparator.comparingDouble((Ratings.Rating r) -> -of(r)).thenComparingInt(Ratings.Rating::playerId);
    }
}
