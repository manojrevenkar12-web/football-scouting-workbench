package scout.lab;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import scout.core.Data;
import scout.core.OpponentModel;
import scout.core.Ratings;
import scout.core.Settings;
import scout.world.World;

/**
 * Can a scout find the best young progressors? The truth is each player's progression ability. A design ranks the
 * under-24 outfield players by its estimate; we compare its top list with the truly best.
 */
public final class RankingStudy {

    public record Design(String id, String label, Ratings.Options options) {}

    public static final List<Design> DESIGNS = List.of(
        new Design("totals", "Season totals (the default sort)", new Ratings.Options(Ratings.Basis.TOTAL, false, false, 0)),
        new Design("per90", "Per 90, at least 450 minutes", new Ratings.Options(Ratings.Basis.PER90, false, false, 450)),
        new Design("per_action", "Per action, at least 450 minutes", new Ratings.Options(Ratings.Basis.PER_ACTION, false, false, 450)),
        new Design("platform", "Per action, opponent-adjusted, shrunk", Ratings.Options.PLATFORM),
        new Design("platform_no_opponent", "Platform without opponent adjustment", new Ratings.Options(Ratings.Basis.PER_ACTION, false, true, 0)),
        new Design("platform_no_shrinkage", "Platform without shrinkage (450 minutes floor)", new Ratings.Options(Ratings.Basis.PER_ACTION, true, false, 450)));

    static final Set<String> OUTFIELD = Set.of("CB", "FB", "DM", "CM", "AM", "W", "ST");
    static final int K = 20;
    static final int MAX_AGE = 23;
    static final int TRUTH_MIN_MINUTES = 270;

    private RankingStudy() {}

    public static Map<String, Map<String, Double>> run(World w, Settings s) {
        World.Observed o = w.observed();
        List<Data.PlayerRow> players = Adapt.players(o);
        Map<Integer, Data.TeamRow> teams = Adapt.teams(o);
        List<Data.AppearanceRow> apps = Adapt.appearances(o);
        OpponentModel opp = OpponentModel.fit(apps, s.opponentModel());
        Map<Integer, Integer> minutes = new java.util.HashMap<>();
        for (Data.AppearanceRow a : apps) minutes.merge(a.playerId(), a.minutes(), Integer::sum);

        // The truth: the best young outfield players by real progression ability, among those who played at all.
        World.Truth t = w.truth();
        List<Data.PlayerRow> pool = players.stream().filter(p -> OUTFIELD.contains(p.position()) && p.age() <= MAX_AGE && minutes.getOrDefault(p.id(), 0) >= TRUTH_MIN_MINUTES).toList();
        List<Integer> best = pool.stream().sorted(Comparator.comparingDouble((Data.PlayerRow p) -> -t.abilities().get(p.id()).progression()).thenComparingInt(Data.PlayerRow::id))
            .limit(K).map(Data.PlayerRow::id).toList();
        Set<Integer> bestSet = new HashSet<>(best);
        Set<Integer> poolIds = new HashSet<>(pool.stream().map(Data.PlayerRow::id).toList());
        Map<Integer, Integer> leagueOf = new java.util.HashMap<>();
        for (Data.PlayerRow p : players) leagueOf.put(p.id(), teams.get(p.teamId()).leagueId());

        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (Design d : DESIGNS) {
            List<Ratings.Rating> ratings = Ratings.compute(players, teams, apps, opp, s.shrinkage(), d.options());
            List<Ratings.Rating> ranked = ratings.stream().filter(r -> poolIds.contains(r.playerId()))
                .sorted(Comparator.comparingDouble((Ratings.Rating r) -> -r.progression()).thenComparingInt(Ratings.Rating::playerId)).toList();
            List<Ratings.Rating> top = ranked.subList(0, Math.min(K, ranked.size()));
            long hits = top.stream().filter(r -> bestSet.contains(r.playerId())).count();
            long topLeague = top.stream().filter(r -> leagueOf.get(r.playerId()) == 1).count();
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("precisionAt20", hits / (double) K);
            m.put("topLeagueShare", top.isEmpty() ? 0 : topLeague / (double) top.size());
            m.put("spearman", spearman(ranked, t));
            m.put("covered", ranked.size() / (double) poolIds.size());
            out.put(d.id(), m);
        }
        Map<String, Double> truthMeta = new LinkedHashMap<>();
        truthMeta.put("topLeagueShare", best.stream().filter(id -> leagueOf.get(id) == 1).count() / (double) K);
        truthMeta.put("poolSize", (double) pool.size());
        out.put("_truth", truthMeta);
        return out;
    }

    /** Rank correlation between a design's ranking and the true ability over the players it rated. */
    static double spearman(List<Ratings.Rating> ranked, World.Truth t) {
        int n = ranked.size();
        if (n < 3) return 0;
        List<Ratings.Rating> byTruth = ranked.stream().sorted(Comparator.comparingDouble((Ratings.Rating r) -> -t.abilities().get(r.playerId()).progression()).thenComparingInt(Ratings.Rating::playerId)).toList();
        Map<Integer, Integer> rt = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) rt.put(byTruth.get(i).playerId(), i);
        double d2 = 0;
        for (int i = 0; i < n; i++) {
            double d = i - rt.get(ranked.get(i).playerId());
            d2 += d * d;
        }
        return 1 - 6 * d2 / (n * ((double) n * n - 1));
    }
}
