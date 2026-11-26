package scout.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RatingsTest {

    /** Two leagues, a strong one (teams 1-2) and a weak one (3-4), linked by cup matches. */
    static List<Data.AppearanceRow> season(double strong, double weak) {
        List<Data.AppearanceRow> out = new ArrayList<>();
        int m = 1;
        int[][] fixtures = {{1, 2}, {2, 1}, {1, 2}, {2, 1}, {3, 4}, {4, 3}, {3, 4}, {4, 3}, {1, 3}, {3, 1}, {2, 4}, {4, 2}};
        for (int round = 0; round < 10; round++) {
            for (int[] f : fixtures) {
                double defOpp = f[1] <= 2 ? strong : weak;
                int actions = 600;
                int bypassed = (int) Math.round(actions * 0.8 * Math.exp(-defOpp));
                out.add(new Data.AppearanceRow(m++, 100 + f[0], f[0], f[1], 90, actions, bypassed, 0.1, 1, 0, 3));
            }
        }
        return out;
    }

    @Test
    void theOpponentModelFindsTheStrongerLeague() {
        var opp = OpponentModel.fit(season(0.4, -0.4), new Settings.OpponentModel(50, 0.1));
        assertThat(opp.defence(1)).isGreaterThan(opp.defence(3) + 0.5);
        assertThat(opp.defence(2)).isGreaterThan(opp.defence(4) + 0.5);
    }

    @Test
    void adjustmentEqualizesPlayersWhoFacedDifferentOpposition() {
        List<Data.AppearanceRow> apps = season(0.4, -0.4);
        var teams = Map.of(1, new Data.TeamRow(1, 1, "A"), 2, new Data.TeamRow(2, 1, "B"), 3, new Data.TeamRow(3, 2, "C"), 4, new Data.TeamRow(4, 2, "D"));
        var players = List.of(new Data.PlayerRow(101, 1, "a", "CM", 22, "right"), new Data.PlayerRow(103, 3, "c", "CM", 22, "right"));
        var opp = OpponentModel.fit(apps, new Settings.OpponentModel(50, 0.1));
        var raw = Ratings.compute(players, teams, apps, opp, new Settings.Shrinkage(0), new Ratings.Options(Ratings.Basis.PER_ACTION, false, false, 0));
        var adj = Ratings.compute(players, teams, apps, opp, new Settings.Shrinkage(0), new Ratings.Options(Ratings.Basis.PER_ACTION, true, false, 0));
        double rawGap = raw.get(1).progression() - raw.get(0).progression(), adjGap = adj.get(1).progression() - adj.get(0).progression();
        assertThat(rawGap).isGreaterThan(0.2); // the weak-league player looks much better raw
        assertThat(Math.abs(adjGap)).isLessThan(rawGap / 3);
    }

    @Test
    void shrinkagePullsSmallSamplesTowardThePosition() {
        var teams = Map.of(1, new Data.TeamRow(1, 1, "A"), 2, new Data.TeamRow(2, 1, "B"));
        var players = List.of(new Data.PlayerRow(1, 1, "lucky", "W", 19, "left"), new Data.PlayerRow(2, 1, "regular", "W", 25, "left"));
        var apps = List.of(new Data.AppearanceRow(1, 1, 1, 2, 10, 5, 15, 0, 0, 0, 0), new Data.AppearanceRow(1, 2, 1, 2, 2700, 1500, 1500, 0, 0, 0, 0));
        var none = Ratings.compute(players, teams, apps, null, new Settings.Shrinkage(80), new Ratings.Options(Ratings.Basis.PER_ACTION, false, false, 0));
        var shrunk = Ratings.compute(players, teams, apps, null, new Settings.Shrinkage(80), new Ratings.Options(Ratings.Basis.PER_ACTION, false, true, 0));
        assertThat(none.get(0).progression()).isGreaterThan(0.9);
        assertThat(shrunk.get(0).progression()).isLessThan(0.15);
    }
}
