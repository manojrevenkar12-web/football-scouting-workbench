package scout.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Players who play like a given player: nearest neighbours on standardized ratings within the same position. */
public final class Similarity {

    public record Neighbour(Ratings.Rating player, double distance) {}

    private Similarity() {}

    public static List<Neighbour> similar(List<Ratings.Rating> ratings, int playerId, Settings.Similarity s) {
        Ratings.Rating me = ratings.stream().filter(r -> r.playerId() == playerId).findFirst().orElse(null);
        if (me == null) return List.of();
        List<Ratings.Rating> pool = ratings.stream().filter(r -> r.position().equals(me.position()) && r.minutes() >= s.minMinutes()).toList();
        Map<String, double[]> stats = Map.of(
            "p", meanSd(pool.stream().mapToDouble(Ratings.Rating::progression).toArray()),
            "x", meanSd(pool.stream().mapToDouble(Ratings.Rating::xgPer90).toArray()),
            "d", meanSd(pool.stream().mapToDouble(Ratings.Rating::defensivePer90).toArray()));
        List<Neighbour> out = new ArrayList<>();
        for (Ratings.Rating r : pool) {
            if (r.playerId() == playerId) continue;
            double d = sq(z(r.progression(), stats.get("p")) - z(me.progression(), stats.get("p")))
                + sq(z(r.xgPer90(), stats.get("x")) - z(me.xgPer90(), stats.get("x")))
                + sq(z(r.defensivePer90(), stats.get("d")) - z(me.defensivePer90(), stats.get("d")));
            out.add(new Neighbour(r, Ratings.round(Math.sqrt(d))));
        }
        out.sort(Comparator.comparingDouble(Neighbour::distance).thenComparingInt(n -> n.player().playerId()));
        return out.subList(0, Math.min(s.neighbours(), out.size()));
    }

    private static double[] meanSd(double[] v) {
        double m = 0;
        for (double x : v) m += x;
        m /= Math.max(1, v.length);
        double ss = 0;
        for (double x : v) ss += (x - m) * (x - m);
        return new double[] {m, Math.max(Math.sqrt(ss / Math.max(1, v.length - 1)), 1e-9)};
    }

    private static double z(double x, double[] ms) {
        return (x - ms[0]) / ms[1];
    }

    private static double sq(double x) {
        return x * x;
    }
}
