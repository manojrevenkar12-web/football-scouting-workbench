package scout.lab;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import scout.world.World;

/** Loads a simulated season into Postgres the way a provider feed would: leagues, squads, matches, totals, events. */
public final class DbLoader {
    private DbLoader() {}

    public static long load(Connection c, World w) throws SQLException {
        return load(c, w, Integer.MAX_VALUE);
    }

    /** Loads everything; events only for the first {@code eventMatches} matches (tests keep it small). */
    public static long load(Connection c, World w, int eventMatches) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("TRUNCATE event, appearance, team_defence, player_ratings, match, player, team, league CASCADE");
        }
        CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
        World.Observed o = w.observed();
        copyRows(copy, "league (id, name, country)", o.leagues(), l -> l.id() + "\t" + l.name() + "\t" + l.country());
        copyRows(copy, "team (id, league_id, name)", o.teams(), t -> t.id() + "\t" + t.leagueId() + "\t" + t.name());
        copyRows(copy, "player (id, team_id, name, position, age, foot)", o.players(), p -> p.id() + "\t" + p.teamId() + "\t" + p.name() + "\t" + p.position() + "\t" + p.age() + "\t" + p.foot());
        copyRows(copy, "match (id, league_id, home_team_id, away_team_id, round, kickoff, competition, video_url)", o.matches(), m ->
            m.id() + "\t" + (m.leagueId() == null ? "\\N" : m.leagueId()) + "\t" + m.homeTeamId() + "\t" + m.awayTeamId() + "\t" + m.round() + "\t" + m.kickoff() + "\t" + m.competition()
                + "\thttps://video.example/match/" + m.id());
        copyRows(copy, "appearance (match_id, player_id, team_id, opponent_team_id, from_minute, to_minute, actions, bypassed, xg, shots, goals, defensive)", o.appearances(), a ->
            a.matchId() + "\t" + a.playerId() + "\t" + a.teamId() + "\t" + a.opponentTeamId() + "\t" + a.fromMinute() + "\t" + a.toMinute() + "\t" + a.actions() + "\t" + a.bypassed()
                + "\t" + a.xg() + "\t" + a.shots() + "\t" + a.goals() + "\t" + a.defensive());
        long events = 0;
        StringBuilder sb = new StringBuilder();
        for (World.Match m : o.matches()) {
            if (m.id() > eventMatches) break;
            for (World.Event e : w.events(m.id())) {
                sb.append(e.id()).append('\t').append(e.matchId()).append('\t').append(e.playerId()).append('\t').append(e.teamId()).append('\t').append(e.type()).append('\t')
                    .append(e.second()).append('\t').append(f(e.x())).append('\t').append(f(e.y())).append('\t').append(f(e.endX())).append('\t').append(f(e.endY())).append('\t')
                    .append(e.bypassed()).append('\t').append(f(e.xg())).append('\t').append(e.goal()).append('\n');
                events++;
            }
            if (sb.length() > 8_000_000) {
                copyText(copy, "event (id, match_id, player_id, team_id, type, second, x, y, end_x, end_y, bypassed, xg, goal)", sb);
                sb.setLength(0);
            }
        }
        copyText(copy, "event (id, match_id, player_id, team_id, type, second, x, y, end_x, end_y, bypassed, xg, goal)", sb);
        try (Statement st = c.createStatement()) {
            st.execute("ANALYZE");
        }
        return events;
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static <T> void copyRows(CopyManager copy, String target, List<T> rows, Function<T, String> line) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (T r : rows) sb.append(line.apply(r)).append('\n');
        copyText(copy, target, sb);
    }

    private static void copyText(CopyManager copy, String target, StringBuilder sb) throws SQLException {
        if (sb.isEmpty()) return;
        try {
            copy.copyIn("COPY " + target + " FROM STDIN", new StringReader(sb.toString()));
        } catch (java.io.IOException e) {
            throw new SQLException(e);
        }
    }
}
