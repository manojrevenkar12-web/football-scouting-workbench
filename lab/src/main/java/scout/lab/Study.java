package scout.lab;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import scout.core.Settings;
import scout.world.World;
import scout.world.WorldConfig;

/** Runs both studies over a set of seeds and averages them. */
public final class Study {
    private Study() {}

    public static Map<String, Object> run(Path root, List<Long> seeds) {
        Settings s = Settings.load(root);
        WorldConfig cfg = WorldConfig.load(root);
        List<Map<String, Map<String, Double>>> ranking = new ArrayList<>(), assistant = new ArrayList<>();
        int players = 0, matches = 0, appearances = 0;
        for (long seed : seeds) {
            World w = new World(seed, cfg);
            World.Observed o = w.observed();
            players += o.players().size();
            matches += o.matches().size();
            appearances += o.appearances().size();
            ranking.add(RankingStudy.run(w, s));
            assistant.add(AssistantStudy.run(w, s));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seeds", seeds);
        out.put("leagues", cfg.leagues().size());
        out.put("players", players / seeds.size());
        out.put("matches", matches / seeds.size());
        out.put("appearances", appearances / seeds.size());
        out.put("requests", AssistantStudy.REQUESTS);
        out.put("ranking", mean(ranking));
        out.put("assistant", mean(assistant));
        return out;
    }

    static Map<String, Map<String, Double>> mean(List<Map<String, Map<String, Double>>> runs) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (String d : runs.get(0).keySet()) {
            Map<String, Double> m = new LinkedHashMap<>();
            for (String k : runs.get(0).get(d).keySet()) {
                double sum = 0;
                for (Map<String, Map<String, Double>> r : runs) sum += r.get(d).get(k);
                m.put(k, Math.round(sum / runs.size() * 10_000) / 10_000.0);
            }
            out.put(d, m);
        }
        return out;
    }
}
