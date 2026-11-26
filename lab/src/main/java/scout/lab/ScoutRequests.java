package scout.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import scout.core.Metric;
import scout.core.ScoutQuery;
import scout.world.Rng;
import scout.world.World;

/** Scout requests written from a known intent, so every answer can be checked. Part of the lab, not the platform. */
public final class ScoutRequests {

    public enum Kind { RUNNABLE, UNCOVERED, VAGUE }

    /** gold is the search the scout meant (for RUNNABLE). */
    public record Request(String text, Kind kind, ScoutQuery gold, List<Slot> slots) {}

    /** One phrase of the request and what it means, so the simulated model can quote it. */
    public record Slot(String field, String value, String phrase) {}

    static final String[][] POSITIONS = {{"CB", "centre-backs"}, {"FB", "full-backs"}, {"DM", "defensive midfielders"}, {"CM", "central midfielders"},
        {"AM", "attacking midfielders"}, {"W", "wingers"}, {"ST", "strikers"}};
    static final String[][] SORTS = {{"PROGRESSION", "who break lines"}, {"PROGRESSION", "ranked by progression"}, {"XG_PER90", "ranked by xG"},
        {"DEFENSIVE_PER90", "who win the ball back, ranked by defensive actions"}, {"GOALS", "ranked by goals"}};
    static final String[] UNCOVERED = {"Serie A", "Ligue 1", "the Championship", "La Liga"};

    private ScoutRequests() {}

    public static List<Request> generate(World w, int n) {
        List<Request> out = new ArrayList<>();
        List<World.League> leagues = w.observed().leagues();
        for (int i = 0; i < n; i++) {
            SplittableRandom r = Rng.stream(w.seed, "request", i);
            List<Slot> slots = new ArrayList<>();
            StringBuilder t = new StringBuilder(r.nextDouble() < 0.5 ? "Show me " : "Find ");
            Integer limit = null, minAge = null, maxAge = null, minMinutes = null;
            String foot = null;
            if (r.nextDouble() < 0.4) {
                limit = 5 + 5 * r.nextInt(4);
                String ph = "the top " + limit;
                slots.add(new Slot("limit", String.valueOf(limit), ph));
                t.append(ph).append(' ');
            }
            if (r.nextDouble() < 0.35) {
                foot = r.nextDouble() < 0.7 ? "left" : "right";
                String ph = foot + "-footed";
                slots.add(new Slot("foot", foot, ph));
                t.append(ph).append(' ');
            }
            String[] pos = POSITIONS[r.nextInt(POSITIONS.length)];
            slots.add(new Slot("position", pos[0], pos[1]));
            t.append(pos[1]);
            double ageKind = r.nextDouble();
            Kind kind = Kind.RUNNABLE;
            if (ageKind < 0.35) {
                int a = 21 + r.nextInt(5);
                maxAge = a - 1;
                String ph = "under " + a;
                slots.add(new Slot("maxAge", String.valueOf(maxAge), ph));
                t.append(' ').append(ph);
            } else if (ageKind < 0.55) {
                int a = 21 + r.nextInt(5);
                maxAge = a;
                String ph = a + " or younger";
                slots.add(new Slot("maxAge", String.valueOf(maxAge), ph));
                t.append(", ").append(ph);
            } else if (ageKind < 0.7) {
                minAge = 20 + r.nextInt(3);
                maxAge = minAge + 3 + r.nextInt(3);
                String ph = "aged " + minAge + " to " + maxAge;
                slots.add(new Slot("minAge", String.valueOf(minAge), ph));
                slots.add(new Slot("maxAge", String.valueOf(maxAge), ph));
                t.append(' ').append(ph);
            } else if (ageKind < 0.7 + w.cfg.model().ambiguousShare()) {
                kind = Kind.VAGUE;
                t.insert(t.indexOf(pos[1]), "young ");
                slots.add(new Slot("maxAge", "23", "young"));
            }
            Set<Integer> leagueIds = new java.util.LinkedHashSet<>();
            double lk = r.nextDouble();
            if (lk < 0.12 && kind == Kind.RUNNABLE) {
                kind = Kind.UNCOVERED;
                String name = UNCOVERED[r.nextInt(UNCOVERED.length)];
                slots.add(new Slot("league", name, name.replace("the ", "")));
                t.append(" in ").append(name);
            } else if (lk < 0.75) {
                World.League a = leagues.get(r.nextInt(leagues.size()));
                leagueIds.add(a.id());
                slots.add(new Slot("league", a.name(), a.name()));
                t.append(" in the ").append(a.name());
                if (r.nextDouble() < 0.3) {
                    World.League b = leagues.get(r.nextInt(leagues.size()));
                    if (b.id() != a.id()) {
                        leagueIds.add(b.id());
                        slots.add(new Slot("league", b.name(), b.name()));
                        t.append(" or the ").append(b.name());
                    }
                }
            }
            if (r.nextDouble() < 0.4) {
                minMinutes = 450 * (1 + r.nextInt(4));
                String ph = "at least " + minMinutes + " minutes";
                slots.add(new Slot("minMinutes", String.valueOf(minMinutes), ph));
                t.append(" with ").append(ph);
            }
            String[] sort = SORTS[r.nextInt(SORTS.length)];
            slots.add(new Slot("sortBy", sort[0], sort[1]));
            t.append(' ').append(sort[1]).append('.');
            ScoutQuery gold = new ScoutQuery(Set.of(pos[0]), minAge, maxAge, foot, leagueIds, minMinutes, Metric.valueOf(sort[0]), limit);
            out.add(new Request(t.toString(), kind, gold, slots));
        }
        return out;
    }
}
