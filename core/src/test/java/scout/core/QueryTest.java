package scout.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryTest {
    static final Settings S = Settings.load(Path.of("."));

    @Test
    void valuesNeverEnterTheSqlText() {
        var q = new ScoutQuery(Set.of("CB", "FB"), 18, 23, "left'; DROP TABLE player;--", Set.of(1, 2), 900, Metric.XG_PER90, 10);
        var st = SqlCompiler.compile(q, S.query());
        assertThat(st.sql()).doesNotContain("DROP").doesNotContain("left").contains("ORDER BY xg_per90 DESC, player_id LIMIT ?");
        assertThat(st.params()).contains("left'; DROP TABLE player;--", 900, 10);
        assertThat(q.validate(new Catalog(List.of(new Data.LeagueRow(1, "Bundesliga", "Germany")), Map.of()), S.query()))
            .contains("foot must be left or right", "no league with id 2");
    }

    @Test
    void validationCatchesImpossibleSearches() {
        var c = new Catalog(List.of(new Data.LeagueRow(1, "Bundesliga", "Germany")), Map.of());
        assertThat(new ScoutQuery(Set.of("XX"), 25, 20, null, null, -1, null, 1000).validate(c, S.query()))
            .contains("unknown position XX", "minimum age is above maximum age", "minimum minutes must be between 0 and 6000", "limit must be between 1 and 100");
    }

    static Ratings.Rating r(int id, String pos, int age, String foot, int league, int minutes, double prog) {
        return new Ratings.Rating(id, "P" + id, pos, age, foot, 1, league, minutes, 100, 50, prog, 1, 0.1, 0, 1);
    }

    @Test
    void inMemorySearchFiltersSortsAndBreaksTiesById() {
        List<Ratings.Rating> rs = new ArrayList<>(List.of(r(3, "CB", 20, "left", 1, 900, 0.5), r(1, "CB", 21, "left", 1, 900, 0.5), r(2, "CB", 25, "left", 1, 900, 0.9),
            r(4, "FB", 20, "left", 1, 900, 0.9), r(5, "CB", 20, "right", 1, 900, 0.9), r(6, "CB", 20, "left", 2, 900, 0.9), r(7, "CB", 20, "left", 1, 100, 0.9)));
        var out = Search.run(rs, new ScoutQuery(Set.of("CB"), null, 22, "left", Set.of(1), 450, Metric.PROGRESSION, 5), S.query());
        assertThat(out).extracting(Ratings.Rating::playerId).containsExactly(1, 3);
    }

    @Test
    void similarPlayersShareThePositionAndAreClosestFirst() {
        List<Ratings.Rating> rs = List.of(r(1, "CB", 20, "left", 1, 900, 0.5), r(2, "CB", 20, "left", 1, 900, 0.45), r(3, "CB", 20, "left", 1, 900, -0.8), r(4, "FB", 20, "left", 1, 900, 0.5));
        var n = Similarity.similar(rs, 1, new Settings.Similarity(600, 5));
        assertThat(n).extracting(x -> x.player().playerId()).containsExactly(2, 3);
    }
}
