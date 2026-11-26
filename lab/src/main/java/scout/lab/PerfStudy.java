package scout.lab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import scout.core.Data;
import scout.core.Metric;
import scout.core.OpponentModel;
import scout.core.Ratings;
import scout.core.ScoutQuery;
import scout.core.Settings;
import scout.core.SqlCompiler;
import scout.world.Rng;
import scout.world.World;

/**
 * Profiles the scouting search on PostgreSQL with a season of event data, three ways: aggregating the event table per
 * query, the same with the V2 indexes and per-player lookups, and reading the player_ratings read model. Timings depend
 * on the machine, so the report says where they were measured and CI does not compare them.
 */
public final class PerfStudy {

    static final int RUNS = 25;

    private PerfStudy() {}

    public static Map<String, Object> run(Path root, String url, String user, String pass, long seed) throws Exception {
        Settings s = Settings.load(root);
        World w = new World(seed, scout.world.WorldConfig.load(root));
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            try (Statement st = c.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS perf_lab CASCADE; CREATE SCHEMA perf_lab; SET search_path TO perf_lab");
                st.execute(Files.readString(root.resolve("service/src/main/resources/db/migration/V1__schema.sql")));
            }
            long t0 = System.nanoTime();
            long events = DbLoader.load(c, w);
            out.put("events", events);
            out.put("loadSeconds", Math.round((System.nanoTime() - t0) / 1e8) / 10.0);
            List<ScoutQuery> mix = mix(w, s);
            Map<String, Object> designs = new LinkedHashMap<>();
            designs.put("event_scan", time(c, mix, s, PerfStudy::eventScan));
            try (Statement st = c.createStatement()) {
                st.execute(Files.readString(root.resolve("service/src/main/resources/db/migration/V2__event_indexes.sql")));
                st.execute("ANALYZE");
            }
            designs.put("indexed_lookup", time(c, mix, s, PerfStudy::indexedLookup));
            writeRatings(c, w, s);
            designs.put("read_model", time(c, mix, s, (q, st) -> SqlCompiler.compile(q, st.query())));
            out.put("designs", designs);
            try (Statement st = c.createStatement()) {
                st.execute("DROP SCHEMA perf_lab CASCADE");
            }
        }
        out.put("postgres", "16");
        out.put("seed", seed);
        out.put("queries", mix(w, s).size());
        out.put("runsPerQuery", RUNS);
        return out;
    }

    interface Compile {
        SqlCompiler.Statement apply(ScoutQuery q, Settings s);
    }

    /** The searches a scouting department runs most: a position, an age band, one or two leagues, a sort. */
    static List<ScoutQuery> mix(World w, Settings s) {
        List<ScoutQuery> out = new ArrayList<>();
        String[] pos = {"CB", "FB", "DM", "CM", "AM", "W", "ST", "CM"};
        for (int i = 0; i < pos.length; i++) {
            SplittableRandom r = Rng.stream(w.seed, "perf-query", i);
            Set<Integer> leagues = i % 3 == 0 ? Set.of() : Set.of(1 + r.nextInt(6));
            out.add(new ScoutQuery(Set.of(pos[i]), null, 21 + r.nextInt(5), i % 4 == 0 ? "left" : null, leagues, 450, Metric.PROGRESSION_PER90_RAW, 25));
        }
        return out;
    }

    private static String filters(ScoutQuery q, List<Object> p) {
        StringBuilder sql = new StringBuilder();
        if (!q.positions().isEmpty()) {
            sql.append(" AND p.position IN (").append(String.join(", ", java.util.Collections.nCopies(q.positions().size(), "?"))).append(')');
            p.addAll(q.positions().stream().sorted().toList());
        }
        if (q.maxAge() != null) {
            sql.append(" AND p.age <= ?");
            p.add(q.maxAge());
        }
        if (q.foot() != null) {
            sql.append(" AND p.foot = ?");
            p.add(q.foot());
        }
        if (!q.leagueIds().isEmpty()) {
            sql.append(" AND t.league_id IN (").append(String.join(", ", java.util.Collections.nCopies(q.leagueIds().size(), "?"))).append(')');
            p.addAll(q.leagueIds().stream().sorted().toList());
        }
        return sql.toString();
    }

    /** Aggregate every event, then filter: what a first version of the endpoint does. */
    static SqlCompiler.Statement eventScan(ScoutQuery q, Settings s) {
        List<Object> p = new ArrayList<>();
        String f = filters(q, p);
        List<Object> params = new ArrayList<>();
        params.add(q.effectiveMinMinutes(s.query()));
        params.addAll(p);
        params.add(q.effectiveLimit(s.query()));
        return new SqlCompiler.Statement("""
            SELECT p.id FROM player p JOIN team t ON t.id = p.team_id
            JOIN (SELECT player_id, SUM(bypassed) AS b FROM event WHERE type IN ('PASS', 'CARRY') GROUP BY player_id) ev ON ev.player_id = p.id
            JOIN (SELECT player_id, SUM(to_minute - from_minute) AS m FROM appearance GROUP BY player_id) ap ON ap.player_id = p.id
            WHERE ap.m >= ?""" + f + " ORDER BY ev.b::float8 / ap.m DESC, p.id LIMIT ?", params);
    }

    /** Filter players first, then look up each one's events through the index. */
    static SqlCompiler.Statement indexedLookup(ScoutQuery q, Settings s) {
        List<Object> p = new ArrayList<>();
        String f = filters(q, p);
        List<Object> params = new ArrayList<>(p);
        params.add(q.effectiveMinMinutes(s.query()));
        params.add(q.effectiveLimit(s.query()));
        return new SqlCompiler.Statement("""
            SELECT p.id FROM player p JOIN team t ON t.id = p.team_id
            CROSS JOIN LATERAL (SELECT COALESCE(SUM(e.bypassed), 0) AS b FROM event e WHERE e.player_id = p.id AND e.type IN ('PASS', 'CARRY')) ev
            CROSS JOIN LATERAL (SELECT COALESCE(SUM(a.to_minute - a.from_minute), 0) AS m FROM appearance a WHERE a.player_id = p.id) ap
            WHERE true""" + f + " AND ap.m >= ? ORDER BY ev.b::float8 / NULLIF(ap.m, 0) DESC NULLS LAST, p.id LIMIT ?", params);
    }

    private static Map<String, Object> time(Connection c, List<ScoutQuery> mix, Settings s, Compile compile) throws SQLException {
        List<Double> ms = new ArrayList<>();
        String plan = null;
        for (ScoutQuery q : mix) {
            SqlCompiler.Statement st = compile.apply(q, s);
            try (PreparedStatement ps = c.prepareStatement(st.sql())) {
                for (int i = 0; i < st.params().size(); i++) ps.setObject(i + 1, st.params().get(i));
                for (int k = 0; k < 2; k++) drain(ps); // warm-up
                for (int k = 0; k < RUNS; k++) {
                    long t = System.nanoTime();
                    drain(ps);
                    ms.add((System.nanoTime() - t) / 1e6);
                }
            }
            if (plan == null) plan = topNodes(c, st);
        }
        double[] v = ms.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("p50Ms", round(v[v.length / 2]));
        m.put("p95Ms", round(v[(int) Math.floor(v.length * 0.95)]));
        m.put("maxMs", round(v[v.length - 1]));
        m.put("plan", plan);
        return m;
    }

    private static void drain(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rs.getInt(1);
        }
    }

    /** The plan's node types, outermost first, so the report shows what the database did. */
    private static String topNodes(Connection c, SqlCompiler.Statement st) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("EXPLAIN " + st.sql())) {
            for (int i = 0; i < st.params().size(); i++) ps.setObject(i + 1, st.params().get(i));
            List<String> nodes = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String line = rs.getString(1).trim().replaceFirst("^->\\s*", "");
                    if (line.startsWith("Sort Key") || line.startsWith("Group Key")) continue;
                    if (line.matches("^(Limit|Sort|Hash Join|Nested Loop|Merge Join|Seq Scan|Index Scan|Index Only Scan|Bitmap Heap Scan|HashAggregate|GroupAggregate|Aggregate|Incremental Sort|Gather Merge|Gather).*")) {
                        String node = line.replaceAll("\\s+\\(cost.*", "").replaceAll(" on (\\w+)( \\w+)?", " on $1");
                        if (!nodes.contains(node)) nodes.add(node);
                    }
                }
            }
            return String.join(" > ", nodes.subList(0, Math.min(6, nodes.size())));
        }
    }

    private static void writeRatings(Connection c, World w, Settings s) throws SQLException {
        World.Observed o = w.observed();
        List<Data.AppearanceRow> apps = Adapt.appearances(o);
        Map<Integer, Data.TeamRow> teams = Adapt.teams(o);
        List<Ratings.Rating> rs = Ratings.compute(Adapt.players(o), teams, apps, OpponentModel.fit(apps, s.opponentModel()), s.shrinkage(), Ratings.Options.PLATFORM);
        Map<Integer, String> leagueName = new LinkedHashMap<>();
        for (World.League l : o.leagues()) leagueName.put(l.id(), l.name());
        try (PreparedStatement ps = c.prepareStatement("""
            INSERT INTO player_ratings (player_id, name, position, age, foot, team_id, team_name, league_id, league_name, minutes, actions, bypassed,
              progression, progression_per90_raw, xg_per90, goals, defensive_per90) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""")) {
            for (Ratings.Rating r : rs) {
                Object[] v = {r.playerId(), r.name(), r.position(), r.age(), r.foot(), r.teamId(), teams.get(r.teamId()).name(), r.leagueId(), leagueName.get(r.leagueId()),
                    r.minutes(), r.actions(), r.bypassed(), r.progression(), r.progressionPer90Raw(), r.xgPer90(), r.goals(), r.defensivePer90()};
                for (int i = 0; i < v.length; i++) ps.setObject(i + 1, v[i]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (Statement st = c.createStatement()) {
            st.execute("ANALYZE player_ratings");
        }
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
