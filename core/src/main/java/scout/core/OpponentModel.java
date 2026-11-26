package scout.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How hard each team is to play through, on one scale across leagues.
 *
 * <p>For every team in every match, log(bypassed per action) = attack(team) - defence(opponent). Alternating ridge
 * least squares fits both. Leagues only meet in cup matches, so those games carry the league-to-league comparison.
 */
public final class OpponentModel {

    public record TeamMatch(int matchId, int teamId, int opponentId, int actions, int bypassed) {}

    private final Map<Integer, Double> defence;
    private final Map<Integer, Double> attack;

    private OpponentModel(Map<Integer, Double> attack, Map<Integer, Double> defence) {
        this.attack = attack;
        this.defence = defence;
    }

    public static OpponentModel fit(List<Data.AppearanceRow> apps, Settings.OpponentModel s) {
        Map<String, int[]> agg = new HashMap<>();
        for (Data.AppearanceRow a : apps) {
            int[] v = agg.computeIfAbsent(a.matchId() + ":" + a.teamId() + ":" + a.opponentTeamId(), k -> new int[2]);
            v[0] += a.actions();
            v[1] += a.bypassed();
        }
        List<double[]> obs = agg.entrySet().stream().map(e -> {
            String[] k = e.getKey().split(":");
            double y = Math.log((e.getValue()[1] + 0.5) / (e.getValue()[0] + 1.0));
            return new double[] {Integer.parseInt(k[1]), Integer.parseInt(k[2]), y};
        }).sorted((x, y) -> Double.compare(x[0] * 1e6 + x[1], y[0] * 1e6 + y[1])).toList();
        Map<Integer, Double> att = new HashMap<>(), def = new HashMap<>();
        for (double[] o : obs) {
            att.putIfAbsent((int) o[0], 0.0);
            def.putIfAbsent((int) o[1], 0.0);
        }
        double mean = obs.stream().mapToDouble(o -> o[2]).average().orElse(0);
        for (int it = 0; it < s.iterations(); it++) {
            Map<Integer, double[]> aSum = new HashMap<>(), dSum = new HashMap<>();
            for (double[] o : obs) {
                aSum.computeIfAbsent((int) o[0], k -> new double[2]);
                double[] as = aSum.get((int) o[0]);
                as[0] += o[2] - mean + def.get((int) o[1]);
                as[1] += 1;
            }
            aSum.forEach((t, v) -> att.put(t, v[0] / (v[1] + s.ridge())));
            for (double[] o : obs) {
                dSum.computeIfAbsent((int) o[1], k -> new double[2]);
                double[] ds = dSum.get((int) o[1]);
                ds[0] += att.get((int) o[0]) - (o[2] - mean);
                ds[1] += 1;
            }
            dSum.forEach((t, v) -> def.put(t, v[0] / (v[1] + s.ridge())));
            double c = def.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            def.replaceAll((k, v) -> v - c);
        }
        return new OpponentModel(att, def);
    }

    /** How much harder than average this team is to play through (log scale; 0 is average). */
    public double defence(int teamId) {
        return defence.getOrDefault(teamId, 0.0);
    }

    public double attack(int teamId) {
        return attack.getOrDefault(teamId, 0.0);
    }

    public Map<Integer, Double> defences() {
        return Map.copyOf(defence);
    }
}
