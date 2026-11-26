package scout.core;

/** The rows the platform works on, whatever the source (the provider's feed, the database, the lab). */
public final class Data {
    private Data() {}

    public record LeagueRow(int id, String name, String country) {}

    public record TeamRow(int id, int leagueId, String name) {}

    public record PlayerRow(int id, int teamId, String name, String position, int age, String foot) {}

    /** One player in one match: the provider's totals. */
    public record AppearanceRow(int matchId, int playerId, int teamId, int opponentTeamId, int minutes,
                                int actions, int bypassed, double xg, int shots, int goals, int defensive) {}
}
