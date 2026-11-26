package scout.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import scout.core.Assistant;
import scout.world.Rng;
import scout.world.WorldConfig;

/** A simulated model with the mistakes real ones make: misspelt leagues, off-by-one ages, dropped filters, wrong sorts. */
public final class SimScoutModel implements Assistant.Model {
    private static final Map<String, String> NAMES = Map.of("PROGRESSION", "progression", "XG_PER90", "xg", "GOALS", "goals", "DEFENSIVE_PER90", "defensive actions", "MINUTES", "most minutes");
    private final long seed;
    private final WorldConfig.ModelErrors e;
    private final Map<String, ScoutRequests.Request> byText;

    public SimScoutModel(long seed, WorldConfig.ModelErrors e, List<ScoutRequests.Request> requests) {
        this.seed = seed;
        this.e = e;
        this.byText = new java.util.HashMap<>();
        for (ScoutRequests.Request r : requests) byText.put(r.text(), r);
    }

    @Override
    public Assistant.Proposal propose(String text) {
        ScoutRequests.Request req = byText.get(text);
        if (req == null) return null;
        SplittableRandom r = Rng.stream(seed, "model", text);
        List<Assistant.Constraint> out = new ArrayList<>();
        for (ScoutRequests.Slot s : req.slots()) {
            switch (s.field()) {
                case "league" -> {
                    if (r.nextDouble() < e.leagueNameError()) {
                        String typo = s.value().length() > 5 ? s.value().substring(0, s.value().length() - 2) + s.value().charAt(s.value().length() - 1) : s.value() + "e";
                        out.add(new Assistant.Constraint("league", typo, typo));
                    } else out.add(new Assistant.Constraint("league", s.value(), s.phrase()));
                }
                case "maxAge", "minAge" -> {
                    String v = s.value();
                    if (r.nextDouble() < e.ageOffByOne()) v = String.valueOf(Integer.parseInt(v) + (r.nextBoolean() ? 1 : -1));
                    out.add(new Assistant.Constraint(s.field(), v, s.phrase()));
                }
                case "foot" -> {
                    if (r.nextDouble() >= e.footDropped()) out.add(new Assistant.Constraint("foot", s.value(), s.phrase()));
                }
                case "sortBy" -> {
                    String v = r.nextDouble() < e.unknownMetric() ? (s.value().equals("XG_PER90") ? "GOALS" : "XG_PER90") : s.value();
                    out.add(new Assistant.Constraint("sortBy", NAMES.get(v), s.phrase()));
                }
                default -> out.add(new Assistant.Constraint(s.field(), s.value(), s.phrase()));
            }
        }
        return new Assistant.Proposal(out);
    }
}
