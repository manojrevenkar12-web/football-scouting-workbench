package scout.lab;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import scout.core.Assistant;
import scout.core.Catalog;
import scout.core.Data;
import scout.core.OpponentModel;
import scout.core.Ratings;
import scout.core.ScoutQuery;
import scout.core.Search;
import scout.core.Settings;
import scout.world.World;

/**
 * Scouting requests in plain language, answered two ways: the model's constraints run as they are, or the assistant
 * where code checks every quote, reads every number itself and asks back. An answer is right when it returns the
 * players the scout meant, or says honestly that a league is not covered, or asks when the request was vague.
 */
public final class AssistantStudy {

    public static final int REQUESTS = 300;

    private AssistantStudy() {}

    public static Map<String, Map<String, Double>> run(World w, Settings s) {
        World.Observed o = w.observed();
        List<Data.PlayerRow> players = Adapt.players(o);
        Map<Integer, Data.TeamRow> teams = Adapt.teams(o);
        List<Data.AppearanceRow> apps = Adapt.appearances(o);
        List<Ratings.Rating> ratings = Ratings.compute(players, teams, apps, OpponentModel.fit(apps, s.opponentModel()), s.shrinkage(), Ratings.Options.PLATFORM);
        Catalog catalog = new Catalog(Adapt.leagues(o), s.leagueAliases());
        List<ScoutRequests.Request> reqs = ScoutRequests.generate(w, REQUESTS);
        SimScoutModel model = new SimScoutModel(w.seed, w.cfg.model(), reqs);

        int[] unchecked = new int[3], platform = new int[3]; // right, wrong shown, asked
        for (ScoutRequests.Request r : reqs) {
            Assistant.Proposal p = model.propose(r.text());
            List<Integer> gold = r.kind() == ScoutRequests.Kind.RUNNABLE ? ids(Search.run(ratings, r.gold(), s.query())) : null;
            // Without checks: whatever the model produced runs.
            ScoutQuery q = Assistant.unchecked(p, catalog);
            boolean ok = gold != null && q.validate(catalog, s.query()).isEmpty() && ids(Search.run(ratings, q, s.query())).equals(gold);
            unchecked[ok ? 0 : 1]++;
            // With checks.
            Assistant.Resolution res = Assistant.resolve(r.text(), p, catalog, s);
            switch (res.status()) {
                case RUN -> platform[gold != null && ids(Search.run(ratings, res.query(), s.query())).equals(gold) ? 0 : 1]++;
                case NOT_COVERED -> platform[r.kind() == ScoutRequests.Kind.UNCOVERED ? 0 : 2]++;
                case CLARIFY -> platform[r.kind() == ScoutRequests.Kind.VAGUE ? 0 : 2]++;
            }
        }
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        out.put("model_unchecked", rates(unchecked));
        out.put("assistant", rates(platform));
        return out;
    }

    private static Map<String, Double> rates(int[] c) {
        double n = c[0] + c[1] + c[2];
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("right", c[0] / n);
        m.put("wrongShown", c[1] / n);
        m.put("asked", c[2] / n);
        return m;
    }

    private static List<Integer> ids(List<Ratings.Rating> rs) {
        return rs.stream().map(Ratings.Rating::playerId).toList();
    }
}
