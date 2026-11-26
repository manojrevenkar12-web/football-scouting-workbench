package scout.lab;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Gates, the README dashboard and REPORT.md, generated from reports/*.json. */
final class Report {

    static final Map<String, String> RANKING_LABELS = new LinkedHashMap<>();
    static final Map<String, String> PERF_LABELS = Map.of(
        "event_scan", "Aggregate the event table per search",
        "indexed_lookup", "Filter players, then indexed event lookups (V2 indexes)",
        "read_model", "Read the player_ratings read model");

    static {
        for (RankingStudy.Design d : RankingStudy.DESIGNS) RANKING_LABELS.put(d.id(), d.label());
    }

    private Report() {}

    static JsonNode at(JsonNode n, String path) {
        for (String p : path.split("\\.")) n = n == null ? null : n.get(p);
        return n;
    }

    static List<Map<String, Object>> gates(JsonNode res) throws Exception {
        JsonNode g = Cli.JSON.readTree(Files.readString(Cli.ROOT.resolve("config/lab/gates.json"))).get("gates");
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode x : g) {
            double v = at(res, x.get("path").asString()).asDouble();
            double limit = x.has("ref") ? at(res, x.get("ref").asString()).asDouble() * x.get("factor").asDouble() : x.get("value").asDouble();
            boolean pass = switch (x.get("op").asString()) {
                case ">=" -> v >= limit - 1e-12;
                case "<=" -> v <= limit + 1e-12;
                default -> Math.abs(v - limit) < 1e-12;
            };
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", x.get("id").asString());
            m.put("text", x.get("text").asString());
            m.put("pass", pass);
            m.put("value", Math.round(v * 10_000) / 10_000.0);
            m.put("op", x.get("op").asString());
            m.put("limit", Math.round(limit * 10_000) / 10_000.0);
            out.add(m);
        }
        return out;
    }

    static boolean printGates(String name, JsonNode res) throws Exception {
        boolean ok = true;
        for (Map<String, Object> g : gates(res)) {
            ok &= (Boolean) g.get("pass");
            System.out.println(name + " " + ((Boolean) g.get("pass") ? "pass" : "FAIL") + " " + g.get("id") + ": " + g.get("value") + " " + g.get("op") + " " + g.get("limit"));
        }
        return ok;
    }

    static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", 100 * v);
    }

    static String num(double v, int d) {
        return String.format(Locale.ROOT, "%." + d + "f", v);
    }

    static String dashboard(JsonNode dev, JsonNode held, JsonNode perf) throws Exception {
        JsonNode r = dev.get("ranking"), a = dev.get("assistant");
        List<String[]> tiles = new ArrayList<>();
        tiles.add(new String[] {pct(r.get("per90").get("precisionAt20").asDouble()) + " → " + pct(r.get("platform").get("precisionAt20").asDouble()),
            "Best young progressors in the top 20", "per-90 sort vs opponent-adjusted, shrunk per-action rating"});
        tiles.add(new String[] {num(r.get("totals").get("spearman").asDouble(), 2) + " → " + num(r.get("platform").get("spearman").asDouble(), 2),
            "Rank correlation with true ability", "season totals vs the platform's rating"});
        tiles.add(new String[] {pct(r.get("per90").get("topLeagueShare").asDouble()) + " → " + pct(r.get("platform").get("topLeagueShare").asDouble()),
            "Bundesliga players in the top 20", "truly: " + pct(r.get("_truth").get("topLeagueShare").asDouble()) + "; per 90 overrates weaker leagues"});
        tiles.add(new String[] {pct(a.get("model_unchecked").get("wrongShown").asDouble()) + " → " + pct(a.get("assistant").get("wrongShown").asDouble()),
            "Assistant answers that show the wrong players", "model's filters run as they are vs checked against the request"});
        tiles.add(new String[] {pct(a.get("model_unchecked").get("right").asDouble()) + " → " + pct(a.get("assistant").get("right").asDouble()),
            "Requests answered correctly", "asks back on " + pct(a.get("assistant").get("asked").asDouble()) + " it could have run"});
        if (perf != null) {
            JsonNode d = perf.get("designs");
            tiles.add(new String[] {num(d.get("event_scan").get("p95Ms").asDouble(), 0) + " ms → " + num(d.get("read_model").get("p95Ms").asDouble(), 1) + " ms",
                "Search p95 on PostgreSQL", String.format(Locale.ROOT, "%,d events; measured on %s", perf.get("events").asLong(), perf.get("measuredOn").asString())});
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Dev seeds %s: each a simulated season of %d leagues, %,d players and %,d matches. Every design reads the same season.%n%n",
            join(dev.get("seeds")), dev.get("leagues").asInt(), dev.get("players").asInt(), dev.get("matches").asInt()));
        sb.append("| | | |\n|---|---|---|\n");
        for (int i = 0; i < tiles.size(); i += 3) {
            sb.append("|");
            for (int k = i; k < Math.min(i + 3, tiles.size()); k++) sb.append(" **").append(tiles.get(k)[0]).append("**<br>").append(tiles.get(k)[1]).append("<br><sub>").append(tiles.get(k)[2]).append("</sub> |");
            sb.append('\n');
        }
        sb.append("\n### Finding young progressors\n\n").append(rankingTable(r)).append("\n");
        sb.append("\n### The scouting assistant\n\n").append(assistantTable(a, dev.get("requests").asInt())).append("\n");
        if (perf != null) sb.append("\n### Search on PostgreSQL\n\n").append(perfTable(perf)).append("\n");
        sb.append("\n### Gates\n\n| Gate | Dev seeds | Held-out seeds |\n|---|---|---|\n");
        List<Map<String, Object>> gd = gates(dev), gh = held == null ? null : gates(held);
        for (int i = 0; i < gd.size(); i++) {
            Map<String, Object> g = gd.get(i);
            sb.append("| ").append(g.get("text")).append(" | ").append(cell(g)).append(" | ").append(gh == null ? "not run" : cell(gh.get(i))).append(" |\n");
        }
        if (held != null) {
            sb.append("\nHeld-out seeds ").append(join(held.get("seeds"))).append(" ran once, after the gates were frozen; see `reports/heldout.lock`.\n\n");
            sb.append(rankingTable(held.get("ranking"))).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    static String cell(Map<String, Object> g) {
        return ((Boolean) g.get("pass") ? "pass" : "FAIL") + " (" + g.get("value") + " " + g.get("op") + " " + g.get("limit") + ")";
    }

    static String rankingTable(JsonNode r) {
        StringBuilder sb = new StringBuilder("| Ranking | Best 20 found | Rank correlation | Bundesliga share of top 20 | Players rated |\n|---|--:|--:|--:|--:|\n");
        RANKING_LABELS.forEach((id, label) -> {
            JsonNode m = r.get(id);
            sb.append("| ").append(label).append(" | ").append(pct(m.get("precisionAt20").asDouble())).append(" | ").append(num(m.get("spearman").asDouble(), 2))
                .append(" | ").append(pct(m.get("topLeagueShare").asDouble())).append(" | ").append(pct(m.get("covered").asDouble())).append(" |\n");
        });
        return sb.toString();
    }

    static String assistantTable(JsonNode a, int n) {
        return "| " + n + " requests per seed | Right | Wrong players shown | Asked back when it could have run |\n|---|--:|--:|--:|\n"
            + "| Model's filters run as they are | " + pct(a.get("model_unchecked").get("right").asDouble()) + " | " + pct(a.get("model_unchecked").get("wrongShown").asDouble()) + " | " + pct(a.get("model_unchecked").get("asked").asDouble()) + " |\n"
            + "| Model proposes, code checks | " + pct(a.get("assistant").get("right").asDouble()) + " | " + pct(a.get("assistant").get("wrongShown").asDouble()) + " | " + pct(a.get("assistant").get("asked").asDouble()) + " |\n";
    }

    static String perfTable(JsonNode p) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%d searches, %d runs each, on PostgreSQL %s with %,d events (measured on %s; CI does not compare timings).%n%n",
            p.get("queries").asInt(), p.get("runsPerQuery").asInt(), p.get("postgres").asString(), p.get("events").asLong(), p.get("measuredOn").asString()));
        sb.append("| Search | p50 | p95 | Plan |\n|---|--:|--:|---|\n");
        PERF_LABELS.keySet().stream().sorted((x, y) -> List.of("event_scan", "indexed_lookup", "read_model").indexOf(x) - List.of("event_scan", "indexed_lookup", "read_model").indexOf(y)).forEach(id -> {
            JsonNode m = p.get("designs").get(id);
            sb.append("| ").append(PERF_LABELS.get(id)).append(" | ").append(num(m.get("p50Ms").asDouble(), 1)).append(" ms | ").append(num(m.get("p95Ms").asDouble(), 1)).append(" ms | ")
                .append(m.get("plan").asString().replace("|", "/")).append(" |\n");
        });
        return sb.toString();
    }

    static String full(JsonNode dev, JsonNode held, JsonNode perf) throws Exception {
        StringBuilder sb = new StringBuilder("# Report\n\nGenerated by `./gradlew :lab:run --args=report` from `reports/`. Do not edit.\n\n## Dev seeds\n\n");
        sb.append(rankingTable(dev.get("ranking"))).append('\n').append(assistantTable(dev.get("assistant"), dev.get("requests").asInt()));
        if (held != null) sb.append("\n## Held-out seeds\n\n").append(rankingTable(held.get("ranking"))).append('\n').append(assistantTable(held.get("assistant"), held.get("requests").asInt()));
        if (perf != null) sb.append("\n## PostgreSQL\n\n").append(perfTable(perf));
        sb.append("\n## Gates\n\n");
        for (Map<String, Object> g : gates(dev)) sb.append("- dev: ").append(cell(g)).append(": ").append(g.get("text")).append('\n');
        if (held != null) for (Map<String, Object> g : gates(held)) sb.append("- held-out: ").append(cell(g)).append(": ").append(g.get("text")).append('\n');
        return sb.toString();
    }

    static String join(JsonNode arr) {
        List<String> s = new ArrayList<>();
        for (JsonNode n : arr) s.add(n.asString());
        return String.join(", ", s);
    }
}
