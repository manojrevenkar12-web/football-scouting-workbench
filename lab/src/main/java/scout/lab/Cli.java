package scout.lab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import scout.world.World;
import scout.world.WorldConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** dev [--check] | heldout | gate | report | perf | load-db */
public final class Cli {

    static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    static final Path ROOT = Path.of(".");
    static final List<String> CONFIG = List.of("config/lab/world.json", "config/lab/seeds.json", "config/lab/gates.json", "config/platform.json");

    private Cli() {}

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "";
        int code = switch (cmd) {
            case "dev" -> dev(List.of(args).contains("--check"));
            case "heldout" -> heldout();
            case "gate" -> gate();
            case "report" -> report();
            case "perf" -> perf();
            case "load-db" -> loadDb(args.length > 1 ? Long.parseLong(args[1]) : 77);
            default -> {
                System.out.println("usage: dev [--check] | heldout | gate | report | perf | load-db [seed]");
                yield 2;
            }
        };
        System.exit(code);
    }

    static List<Long> seeds(String which) throws Exception {
        List<Long> out = new ArrayList<>();
        for (JsonNode n : JSON.readTree(Files.readString(ROOT.resolve("config/lab/seeds.json"))).get(which)) out.add(n.asLong());
        return out;
    }

    static String write(Object o) {
        return JSON.writeValueAsString(o) + "\n";
    }

    static int dev(boolean check) throws Exception {
        String res = write(Study.run(ROOT, seeds("dev")));
        Path p = ROOT.resolve("reports/dev.json");
        if (check) {
            if (!Files.exists(p) || !Files.readString(p).equals(res)) {
                System.out.println("dev results differ from reports/dev.json; run the dev study and commit the result");
                return 1;
            }
            System.out.println("dev results reproduce exactly");
            return gate();
        }
        Files.createDirectories(p.getParent());
        Files.writeString(p, res);
        Report.printGates("dev", JSON.readTree(res));
        return 0;
    }

    static int heldout() throws Exception {
        Path lock = ROOT.resolve("reports/heldout.lock");
        if (Files.exists(lock)) {
            System.out.println("held-out seeds already ran (" + Files.readString(lock).trim() + "); they run once");
            return 1;
        }
        String res = write(Study.run(ROOT, seeds("heldout")));
        Files.writeString(ROOT.resolve("reports/heldout.json"), res);
        Files.writeString(lock, "ran " + Instant.now().toString().substring(0, 19) + "Z on config " + configHash() + "\n");
        Report.printGates("heldout", JSON.readTree(res));
        return 0;
    }

    static int gate() throws Exception {
        boolean ok = true;
        for (String n : List.of("dev", "heldout")) {
            Path p = ROOT.resolve("reports/" + n + ".json");
            if (!Files.exists(p)) continue;
            boolean pass = Report.printGates(n, JSON.readTree(Files.readString(p)));
            if (n.equals("dev")) ok &= pass;
        }
        return ok ? 0 : 1;
    }

    static int report() throws Exception {
        JsonNode dev = JSON.readTree(Files.readString(ROOT.resolve("reports/dev.json")));
        Path hp = ROOT.resolve("reports/heldout.json"), pp = ROOT.resolve("reports/perf.json");
        JsonNode held = Files.exists(hp) ? JSON.readTree(Files.readString(hp)) : null;
        JsonNode perf = Files.exists(pp) ? JSON.readTree(Files.readString(pp)) : null;
        Files.writeString(ROOT.resolve("REPORT.md"), Report.full(dev, held, perf));
        String readme = Files.readString(ROOT.resolve("README.md"));
        String a = "<!-- results:start -->", b = "<!-- results:end -->";
        int i = readme.indexOf(a), j = readme.indexOf(b);
        Files.writeString(ROOT.resolve("README.md"), readme.substring(0, i + a.length()) + "\n" + Report.dashboard(dev, held, perf) + "\n" + readme.substring(j));
        Map<String, Object> web = new LinkedHashMap<>();
        web.put("dev", dev);
        web.put("heldout", held);
        web.put("perf", perf);
        web.put("labels", Report.RANKING_LABELS);
        web.put("perfLabels", new java.util.TreeMap<>(Report.PERF_LABELS));
        Map<String, Object> gates = new LinkedHashMap<>();
        gates.put("dev", Report.gates(dev));
        gates.put("heldout", held == null ? List.of() : Report.gates(held));
        web.put("gates", gates);
        Files.createDirectories(ROOT.resolve("web/public/data"));
        Files.writeString(ROOT.resolve("web/public/data/results.json"), write(web));
        System.out.println("wrote REPORT.md, the README dashboard and web/public/data/results.json");
        return 0;
    }

    static int perf() throws Exception {
        String url = env("SCOUT_DB_URL", "jdbc:postgresql://localhost:5432/scouting");
        Map<String, Object> res = PerfStudy.run(ROOT, url, env("SCOUT_DB_USER", "scout"), env("SCOUT_DB_PASSWORD", "scout"), seeds("dev").get(0));
        res.put("measuredOn", env("SCOUT_PERF_MACHINE", "a 2-core development VM"));
        Files.writeString(ROOT.resolve("reports/perf.json"), write(res));
        System.out.println(write(res));
        return 0;
    }

    static int loadDb(long seed) throws Exception {
        String url = env("SCOUT_DB_URL", "jdbc:postgresql://localhost:5432/scouting");
        try (var c = java.sql.DriverManager.getConnection(url, env("SCOUT_DB_USER", "scout"), env("SCOUT_DB_PASSWORD", "scout"))) {
            long n = DbLoader.load(c, new World(seed, WorldConfig.load(ROOT)));
            System.out.println("loaded season " + seed + ": " + n + " events");
        }
        return 0;
    }

    static String env(String k, String d) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? d : v;
    }

    static String configHash() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String f : CONFIG) md.update(Files.readAllBytes(ROOT.resolve(f)));
        return HexFormat.of().formatHex(md.digest()).substring(0, 16);
    }
}
