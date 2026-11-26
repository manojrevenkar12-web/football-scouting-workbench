package scout.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Season ratings per player. Progression is "opponents bypassed per on-ball action": per action rather than per 90 so
 * a possession-heavy team does not inflate it, adjusted for how hard the opponents were to play through, and shrunk
 * toward the position's average by how many actions we have seen.
 */
public final class Ratings {

    public enum Basis { TOTAL, PER90, PER_ACTION }

    /** Which corrections to apply. The platform uses all of them; the lab switches them off one by one. */
    public record Options(Basis basis, boolean opponentAdjusted, boolean shrunk, int minMinutes) {
        public static final Options PLATFORM = new Options(Basis.PER_ACTION, true, true, 0);
    }

    public record Rating(int playerId, String name, String position, int age, String foot, int teamId, int leagueId,
                         int minutes, int actions, int bypassed, double progression, double progressionPer90Raw,
                         double xgPer90, int goals, double defensivePer90) {}

    private Ratings() {}

    public static List<Rating> compute(List<Data.PlayerRow> players, Map<Integer, Data.TeamRow> teams, List<Data.AppearanceRow> apps,
                                       OpponentModel opp, Settings.Shrinkage shrink, Options o) {
        Map<Integer, double[]> agg = new HashMap<>(); // minutes, actions, bypassed, exposure, xg, goals, defensive
        for (Data.AppearanceRow a : apps) {
            double[] v = agg.computeIfAbsent(a.playerId(), k -> new double[7]);
            v[0] += a.minutes();
            v[1] += a.actions();
            v[2] += a.bypassed();
            v[3] += a.actions() * (o.opponentAdjusted() && opp != null ? Math.exp(-opp.defence(a.opponentTeamId())) : 1.0);
            v[4] += a.xg();
            v[5] += a.goals();
            v[6] += a.defensive();
        }
        // The position's average rate is the prior a player with few actions is pulled toward.
        Map<String, double[]> pos = new HashMap<>(); // bypassed, exposure, minutes
        for (Data.PlayerRow p : players) {
            double[] v = agg.get(p.id());
            if (v == null) continue;
            double[] q = pos.computeIfAbsent(p.position(), k -> new double[3]);
            q[0] += v[2];
            q[1] += v[3];
            q[2] += v[0];
        }
        List<Rating> out = new ArrayList<>();
        for (Data.PlayerRow p : players) {
            double[] v = agg.get(p.id());
            if (v == null || v[0] == 0 || v[0] < o.minMinutes()) continue;
            double[] q = pos.get(p.position());
            double prior = q[1] > 0 ? q[0] / q[1] : 0;
            double score = switch (o.basis()) {
                case TOTAL -> v[2];
                case PER90 -> Math.log(Math.max(v[2] / v[0], 1e-9) / Math.max(q[0] / q[2], 1e-9));
                case PER_ACTION -> {
                    double rate = o.shrunk()
                        ? (v[2] + shrink.priorActions() * prior) / (v[3] + shrink.priorActions())
                        : (v[3] > 0 ? v[2] / v[3] : 0);
                    yield Math.log(Math.max(rate, 1e-9) / Math.max(prior, 1e-9));
                }
            };
            Data.TeamRow t = teams.get(p.teamId());
            out.add(new Rating(p.id(), p.name(), p.position(), p.age(), p.foot(), p.teamId(), t == null ? 0 : t.leagueId(), (int) v[0], (int) v[1], (int) v[2],
                round(score), round(v[2] / v[0] * 90), round(v[4] / v[0] * 90), (int) v[5], round(v[6] / v[0] * 90)));
        }
        return out;
    }

    static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}
