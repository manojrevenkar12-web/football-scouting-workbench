package scout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import scout.core.Metric;
import scout.core.OpponentModel;
import scout.core.Ratings;
import scout.core.ScoutQuery;
import scout.core.Search;
import scout.core.Settings;
import scout.lab.Adapt;
import scout.lab.DbLoader;
import scout.world.World;
import scout.world.WorldConfig;

/** The service on a real PostgreSQL. Runs when SCOUT_TEST_DB_URL points at an empty database (CI starts one). */
@SpringBootTest(properties = {"scout.admin-key=test-admin", "scout.refresh-on-start=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SCOUT_TEST_DB_URL", matches = ".+")
class ServiceIT {
    static final String URL = System.getenv("SCOUT_TEST_DB_URL");
    static final String USER = System.getenv().getOrDefault("SCOUT_TEST_DB_USER", "scout");
    static final String PASS = System.getenv().getOrDefault("SCOUT_TEST_DB_PASSWORD", "scout");
    static final World WORLD = new World(42, WorldConfig.load(Path.of(".")));
    static boolean loaded;

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> URL);
        r.add("spring.datasource.username", () -> USER);
        r.add("spring.datasource.password", () -> PASS);
    }

    @Autowired MockMvc mvc;
    @Autowired RatingsService ratings;
    @Autowired ScoutRepository repo;
    @Autowired Settings settings;

    @BeforeEach
    void load() throws Exception {
        if (loaded) return;
        try (var c = DriverManager.getConnection(URL, USER, PASS)) {
            DbLoader.load(c, WORLD, 30);
        }
        ratings.refresh();
        loaded = true;
    }

    @Test
    void sqlAndInMemorySearchReturnTheSamePlayers() {
        var o = WORLD.observed();
        var apps = Adapt.appearances(o);
        List<Ratings.Rating> rs = Ratings.compute(Adapt.players(o), Adapt.teams(o), apps, OpponentModel.fit(apps, settings.opponentModel()), settings.shrinkage(), Ratings.Options.PLATFORM);
        String[] pos = {"CB", "FB", "DM", "CM", "AM", "W", "ST", "GK"};
        for (int i = 0; i < 30; i++) {
            SplittableRandom r = new SplittableRandom(i);
            var q = new ScoutQuery(Set.of(pos[r.nextInt(pos.length)]), r.nextBoolean() ? 18 + r.nextInt(4) : null, r.nextBoolean() ? 22 + r.nextInt(8) : null,
                r.nextInt(3) == 0 ? "left" : null, r.nextBoolean() ? Set.of(1 + r.nextInt(6)) : Set.of(), r.nextInt(4) * 450, Metric.values()[r.nextInt(Metric.values().length)], 5 + r.nextInt(30));
            assertThat(repo.search(q, settings).stream().map(ScoutRepository.PlayerCard::playerId).toList())
                .as(q.toString()).isEqualTo(Search.run(rs, q, settings.query()).stream().map(Ratings.Rating::playerId).toList());
        }
    }

    @Test
    void searchValidatesItsInput() throws Exception {
        mvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content("{\"positions\":[\"CB\"],\"maxAge\":22,\"limit\":5}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5)).andExpect(jsonPath("$[0].position").value("CB"));
        mvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content("{\"positions\":[\"XX\"],\"limit\":500}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.problems[0]").value("unknown position XX"));
        mvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content("{\"sortBy\":\"progression; DROP TABLE player\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content("not json")).andExpect(status().isBadRequest());
    }

    @Test
    void theAssistantRunsChecksOrAsks() throws Exception {
        mvc.perform(post("/api/assistant").contentType(MediaType.APPLICATION_JSON)
                .content("{\"request\":\"Find left-footed centre-backs under 23 in the Eredivisie who break lines\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUN")).andExpect(jsonPath("$.source").value("rules"))
            .andExpect(jsonPath("$.query.maxAge").value(22)).andExpect(jsonPath("$.results[0].foot").value("left"));
        mvc.perform(post("/api/assistant").contentType(MediaType.APPLICATION_JSON).content("{\"request\":\"young wingers in Serie A\"}"))
            .andExpect(jsonPath("$.status").value("NOT_COVERED")).andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void playerCardSimilarPlayersAndVideoMoments() throws Exception {
        int id = repo.search(new ScoutQuery(Set.of("CM"), null, null, null, Set.of(), 900, Metric.PROGRESSION, 1), settings).get(0).playerId();
        mvc.perform(get("/api/players/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.player.position").value("CM"))
            .andExpect(jsonPath("$.percentiles.progression").isNumber());
        mvc.perform(get("/api/players/" + id + "/similar")).andExpect(status().isOk()).andExpect(jsonPath("$[0].player.position").value("CM"));
        mvc.perform(get("/api/players/999999")).andExpect(status().isNotFound());
        int withEvents = WORLD.observed().appearances().stream().filter(a -> a.matchId() == 3 && a.actions() > 20).findFirst().orElseThrow().playerId();
        mvc.perform(get("/api/players/" + withEvents + "/moments?limit=4")).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4)).andExpect(jsonPath("$[0].moment.type").value(org.hamcrest.Matchers.oneOf("PASS", "CARRY"))).andExpect(jsonPath("$[0].clip.url").value(org.hamcrest.Matchers.startsWith("https://video.example/match/")));
    }

    @Test
    void refreshNeedsTheAdminKey() throws Exception {
        mvc.perform(post("/api/admin/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/refresh").header("x-admin-key", "wrong")).andExpect(status().isUnauthorized());
    }

    @Test
    void consoleRoutesLoadTheApp() throws Exception {
        for (String path : List.of("/lab", "/assistant", "/players/12"))
            mvc.perform(get(path)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/index.html"));
        mvc.perform(get("/players/abc")).andExpect(status().isNotFound());
    }
}
