package scout.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class WorldTest {
    static final WorldConfig CFG = WorldConfig.load(Path.of("."));

    @Test
    void theWorldDoesNotImportThePlatformOrReadItsSettings() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("world/src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f);
                assertThat(src).doesNotContain("import scout.core").doesNotContain("\"config/platform");
            }
        }
        assertThat(Files.readString(Path.of("world/build.gradle.kts"))).doesNotContain("project(\":core\")");
    }

    @Test
    void sameSeedSameSeason() {
        var a = new World(11, CFG).observed();
        var b = new World(11, CFG).observed();
        assertThat(a.appearances()).isEqualTo(b.appearances());
        assertThat(new World(12, CFG).observed().appearances()).isNotEqualTo(a.appearances());
    }

    @Test
    void eventsAddUpToTheTotalsAndLeaguesMeetInTheCup() {
        World w = new World(11, CFG);
        var o = w.observed();
        int matchId = 7;
        List<World.Event> ev = w.events(matchId);
        int bypassed = ev.stream().mapToInt(World.Event::bypassed).sum();
        int shots = (int) ev.stream().filter(e -> e.type() == World.EventType.SHOT).count();
        var apps = o.appearances().stream().filter(a -> a.matchId() == matchId).toList();
        assertThat(bypassed).isEqualTo(apps.stream().mapToInt(World.Appearance::bypassed).sum());
        assertThat(shots).isEqualTo(apps.stream().mapToInt(World.Appearance::shots).sum());
        assertThat(apps.stream().filter(a -> a.minutes() == 90).count()).isGreaterThanOrEqualTo(14);
        assertThat(o.matches().stream().filter(m -> m.competition().equals("cup")).count()).isGreaterThan(100);
        assertThat(w.events(matchId)).isEqualTo(ev);
    }

    @Test
    void weakerLeaguesInflateRawRates() {
        World w = new World(11, CFG);
        var o = w.observed();
        var t = w.truth();
        double strong = 0, weak = 0;
        int ns = 0, nw = 0;
        for (var a : o.appearances()) {
            if (a.actions() == 0) continue;
            int league = o.teams().get(a.teamId() - 1).leagueId();
            double r = a.bypassed() / (double) a.actions();
            if (t.leagueStrength().get(league) == 0.0) { strong += r; ns++; }
            if (t.leagueStrength().get(league) <= -0.5) { weak += r; nw++; }
        }
        assertThat(weak / nw).isGreaterThan(1.3 * strong / ns);
    }
}
