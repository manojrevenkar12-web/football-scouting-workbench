package scout.service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import scout.core.Assistant;
import scout.core.Catalog;
import scout.core.Data;
import scout.core.Metric;
import scout.core.Ratings;
import scout.core.ScoutQuery;
import scout.core.Settings;
import scout.core.Similarity;

@RestController
@RequestMapping("/api")
public class ScoutController {
    private final ScoutRepository repo;
    private final RatingsService ratings;
    private final Settings settings;
    private final ScoutApplication.AssistantModels models;
    private final String adminKey;

    public ScoutController(ScoutRepository repo, RatingsService ratings, Settings settings, ScoutApplication.AssistantModels models,
                           @Value("${scout.admin-key}") String adminKey) {
        this.repo = repo;
        this.ratings = ratings;
        this.settings = settings;
        this.models = models;
        this.adminKey = adminKey;
    }

    public record Problems(List<String> problems) {}

    public record SearchRequest(List<String> positions, Integer minAge, Integer maxAge, String foot, List<Integer> leagueIds,
                                Integer minMinutes, String sortBy, Integer limit) {
        ScoutQuery toQuery() {
            Metric m = null;
            if (sortBy != null) {
                try {
                    m = Metric.valueOf(sortBy);
                } catch (IllegalArgumentException e) {
                    throw new BadRequest("unknown sort " + sortBy);
                }
            }
            return new ScoutQuery(positions == null ? null : java.util.Set.copyOf(positions), minAge, maxAge, foot,
                leagueIds == null ? null : java.util.Set.copyOf(leagueIds), minMinutes, m, limit);
        }
    }

    public static final class BadRequest extends RuntimeException {
        public BadRequest(String m) {
            super(m);
        }
    }

    @GetMapping("/leagues")
    public List<Data.LeagueRow> leagues() {
        return ratings.catalog().leagues();
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody SearchRequest body) {
        ScoutQuery q = body.toQuery();
        List<String> problems = q.validate(ratings.catalog(), settings.query());
        if (!problems.isEmpty()) return ResponseEntity.badRequest().body(new Problems(problems));
        return ResponseEntity.ok(repo.search(q, settings));
    }

    @GetMapping("/players/{id}")
    public ResponseEntity<?> player(@PathVariable int id) {
        return repo.card(id).<ResponseEntity<?>>map(c -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("player", c);
            out.put("percentiles", repo.percentiles(c));
            return ResponseEntity.ok(out);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/players/{id}/similar")
    public List<Map<String, Object>> similar(@PathVariable int id) {
        var card = repo.card(id).orElseThrow(() -> new BadRequest("no player " + id));
        // Similarity compares with every rated player of the position, not one page of a search.
        List<Ratings.Rating> pool = repo.position(card.position(), settings.similarity().minMinutes()).stream().map(ScoutController::rating).toList();
        List<Ratings.Rating> withMe = new java.util.ArrayList<>(pool);
        if (pool.stream().noneMatch(r -> r.playerId() == id)) withMe.add(rating(card));
        return Similarity.similar(withMe, id, settings.similarity()).stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("player", repo.card(n.player().playerId()).orElseThrow());
            m.put("distance", n.distance());
            return m;
        }).toList();
    }

    private static Ratings.Rating rating(ScoutRepository.PlayerCard c) {
        return new Ratings.Rating(c.playerId(), c.name(), c.position(), c.age(), c.foot(), 0, c.leagueId(), c.minutes(), c.actions(), c.bypassed(),
            c.progression(), c.progressionPer90Raw(), c.xgPer90(), c.goals(), c.defensivePer90());
    }

    @GetMapping("/players/{id}/moments")
    public List<Map<String, Object>> moments(@PathVariable int id, @RequestParam(defaultValue = "12") int limit) {
        int n = Math.max(1, Math.min(limit, 50));
        return repo.moments(id, n).stream().map(m -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("moment", m);
            int from = Math.max(0, m.second() - 8), to = m.second() + 6;
            out.put("clip", Map.of("from", from, "to", to, "url", m.videoUrl() + "#t=" + from + "," + to));
            return out;
        }).toList();
    }

    public record AssistRequest(String request) {}

    @PostMapping("/assistant")
    public ResponseEntity<?> assistant(@RequestBody AssistRequest body) {
        if (body == null || body.request() == null || body.request().isBlank() || body.request().length() > 500)
            return ResponseEntity.badRequest().body(new Problems(List.of("send a request of 1 to 500 characters")));
        Catalog catalog = ratings.catalog();
        Assistant.Proposal p = models.claude() != null ? models.claude().propose(body.request()) : null;
        String source = "claude";
        if (p == null) {
            p = new Assistant.Rules(catalog).propose(body.request());
            source = "rules";
        }
        Assistant.Resolution r = Assistant.resolve(body.request(), p, catalog, settings);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", r.status());
        out.put("source", source);
        out.put("constraints", p.constraints());
        out.put("query", r.query());
        out.put("messages", r.messages());
        out.put("results", r.status() == Assistant.Status.RUN ? repo.search(r.query(), settings) : List.of());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/admin/refresh")
    public ResponseEntity<?> refresh(@RequestHeader(value = "x-admin-key", required = false) String key) {
        if (adminKey == null || adminKey.isBlank() || key == null
            || !MessageDigest.isEqual(adminKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Problems(List.of("admin key required")));
        return ResponseEntity.ok(Map.of("rated", ratings.refresh()));
    }
}
