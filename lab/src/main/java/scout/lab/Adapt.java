package scout.lab;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import scout.core.Data;
import scout.world.World;

/** What a data provider would deliver: the world's observable records as the platform's rows. No truth crosses here. */
public final class Adapt {
    private Adapt() {}

    public static List<Data.LeagueRow> leagues(World.Observed o) {
        return o.leagues().stream().map(l -> new Data.LeagueRow(l.id(), l.name(), l.country())).toList();
    }

    public static Map<Integer, Data.TeamRow> teams(World.Observed o) {
        return o.teams().stream().collect(Collectors.toMap(World.Team::id, t -> new Data.TeamRow(t.id(), t.leagueId(), t.name())));
    }

    public static List<Data.PlayerRow> players(World.Observed o) {
        return o.players().stream().map(p -> new Data.PlayerRow(p.id(), p.teamId(), p.name(), p.position().name(), p.age(), p.foot())).toList();
    }

    public static List<Data.AppearanceRow> appearances(World.Observed o) {
        return o.appearances().stream().map(a -> new Data.AppearanceRow(a.matchId(), a.playerId(), a.teamId(), a.opponentTeamId(), a.minutes(),
            a.actions(), a.bypassed(), a.xg(), a.shots(), a.goals(), a.defensive())).toList();
    }
}
