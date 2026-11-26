package scout.core;

import java.util.ArrayList;
import java.util.List;

/** Turns a validated {@link ScoutQuery} into one parameterized statement. Values never enter the SQL text. */
public final class SqlCompiler {

    public record Statement(String sql, List<Object> params) {}

    private SqlCompiler() {}

    public static Statement compile(ScoutQuery q, Settings.Query s) {
        StringBuilder sql = new StringBuilder("SELECT player_id FROM player_ratings WHERE minutes >= ?");
        List<Object> p = new ArrayList<>();
        p.add(q.effectiveMinMinutes(s));
        if (!q.positions().isEmpty()) {
            sql.append(" AND position IN (").append(placeholders(q.positions().size())).append(')');
            p.addAll(q.positions().stream().sorted().toList());
        }
        if (q.minAge() != null) {
            sql.append(" AND age >= ?");
            p.add(q.minAge());
        }
        if (q.maxAge() != null) {
            sql.append(" AND age <= ?");
            p.add(q.maxAge());
        }
        if (q.foot() != null) {
            sql.append(" AND foot = ?");
            p.add(q.foot());
        }
        if (!q.leagueIds().isEmpty()) {
            sql.append(" AND league_id IN (").append(placeholders(q.leagueIds().size())).append(')');
            p.addAll(q.leagueIds().stream().sorted().toList());
        }
        // The sort column comes from the Metric enum, never from input.
        sql.append(" ORDER BY ").append(q.sortBy().column).append(" DESC, player_id LIMIT ?");
        p.add(q.effectiveLimit(s));
        return new Statement(sql.toString(), p);
    }

    private static String placeholders(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }
}
