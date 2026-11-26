package scout.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import scout.core.Data;
import scout.core.OpponentModel;
import scout.core.Ratings;
import scout.core.ScoutQuery;
import scout.core.Settings;
import scout.core.SqlCompiler;

/** All SQL the service runs. Searches read the player_ratings read model only. */
@Repository
public class ScoutRepository {
    private final JdbcClient db;

    public ScoutRepository(JdbcClient db) {
        this.db = db;
    }

    public List<Data.LeagueRow> leagues() {
        return db.sql("SELECT id, name, country FROM league ORDER BY id").query(Data.LeagueRow.class).list();
    }

    public Map<Integer, Data.TeamRow> teams() {
        return db.sql("SELECT id, league_id, name FROM team").query(Data.TeamRow.class).list().stream()
            .collect(java.util.stream.Collectors.toMap(Data.TeamRow::id, t -> t));
    }

    public List<Data.PlayerRow> players() {
        return db.sql("SELECT id, team_id, name, position, age, foot FROM player").query(Data.PlayerRow.class).list();
    }

    public List<Data.AppearanceRow> appearances() {
        return db.sql("""
            SELECT match_id, player_id, team_id, opponent_team_id, to_minute - from_minute AS minutes, actions, bypassed, xg::float8 AS xg, shots, goals, defensive
            FROM appearance""").query(Data.AppearanceRow.class).list();
    }

    public long ratedPlayers() {
        return db.sql("SELECT count(*) FROM player_ratings").query(Long.class).single();
    }

    /** Replaces the read model in one transaction: searches see the old ratings or the new ones, never a mix. */
    @Transactional
    public void replaceRatings(List<Ratings.Rating> ratings, OpponentModel opp, Map<Integer, Data.TeamRow> teams, Map<Integer, String> leagueNames) {
        db.sql("DELETE FROM player_ratings").update();
        db.sql("DELETE FROM team_defence").update();
        for (Ratings.Rating r : ratings) {
            db.sql("""
                INSERT INTO player_ratings (player_id, name, position, age, foot, team_id, team_name, league_id, league_name, minutes, actions, bypassed,
                  progression, progression_per90_raw, xg_per90, goals, defensive_per90)
                VALUES (:id, :name, :pos, :age, :foot, :team, :teamName, :league, :leagueName, :minutes, :actions, :bypassed, :prog, :raw, :xg, :goals, :def)""")
                .param("id", r.playerId()).param("name", r.name()).param("pos", r.position()).param("age", r.age()).param("foot", r.foot())
                .param("team", r.teamId()).param("teamName", teams.get(r.teamId()).name()).param("league", r.leagueId()).param("leagueName", leagueNames.get(r.leagueId()))
                .param("minutes", r.minutes()).param("actions", r.actions()).param("bypassed", r.bypassed()).param("prog", r.progression())
                .param("raw", r.progressionPer90Raw()).param("xg", r.xgPer90()).param("goals", r.goals()).param("def", r.defensivePer90()).update();
        }
        for (Map.Entry<Integer, Double> e : opp.defences().entrySet()) {
            db.sql("INSERT INTO team_defence (team_id, defence, attack) VALUES (?, ?, ?)").params(e.getKey(), e.getValue(), opp.attack(e.getKey())).update();
        }
    }

    public record PlayerCard(int playerId, String name, String position, int age, String foot, String teamName, String leagueName, int leagueId, int minutes,
                             int actions, int bypassed, double progression, double progressionPer90Raw, double xgPer90, int goals, double defensivePer90) {}

    private static final String CARD = """
        SELECT player_id, name, position, age, foot, team_name, league_name, league_id, minutes, actions, bypassed, progression, progression_per90_raw,
               xg_per90, goals, defensive_per90 FROM player_ratings""";

    public List<PlayerCard> search(ScoutQuery q, Settings s) {
        SqlCompiler.Statement st = SqlCompiler.compile(q, s.query());
        List<Integer> ids = db.sql(st.sql()).params(st.params()).query(Integer.class).list();
        if (ids.isEmpty()) return List.of();
        Map<Integer, PlayerCard> byId = db.sql(CARD + " WHERE player_id IN (:ids)").param("ids", ids).query(PlayerCard.class).list().stream()
            .collect(java.util.stream.Collectors.toMap(PlayerCard::playerId, c -> c));
        return ids.stream().map(byId::get).toList();
    }

    public List<PlayerCard> position(String position, int minMinutes) {
        return db.sql(CARD + " WHERE position = ? AND minutes >= ? ORDER BY player_id").params(position, minMinutes).query(PlayerCard.class).list();
    }

    public java.util.Optional<PlayerCard> card(int playerId) {
        return db.sql(CARD + " WHERE player_id = ?").param(playerId).query(PlayerCard.class).optional();
    }

    /** Where the player stands in his position: share of players with at least 450 minutes he beats on each rating. */
    public Map<String, Double> percentiles(PlayerCard c) {
        return db.sql("""
            SELECT avg((progression < :p)::int)::float8 AS progression, avg((xg_per90 < :x)::int)::float8 AS xg_per90,
                   avg((defensive_per90 < :d)::int)::float8 AS defensive_per90
            FROM player_ratings WHERE position = :pos AND minutes >= 450""")
            .param("p", c.progression()).param("x", c.xgPer90()).param("d", c.defensivePer90()).param("pos", c.position())
            .query((rs, n) -> Map.of("progression", rs.getDouble(1), "xgPer90", rs.getDouble(2), "defensivePer90", rs.getDouble(3))).single();
    }

    public record Moment(long eventId, int matchId, String type, int second, double x, double y, double endX, double endY, int bypassed, double xg, boolean goal,
                         String opponent, String kickoff, String videoUrl) {}

    /** The player's most line-breaking passes and carries and his best shots, half each when he has enough of both, with where to find them in the video. */
    public List<Moment> moments(int playerId, int limit) {
        List<Moment> actions = moments(playerId, "e.type IN ('PASS', 'CARRY')", "e.bypassed DESC, e.id", limit);
        List<Moment> shots = moments(playerId, "e.type = 'SHOT'", "e.goal DESC, e.xg DESC, e.id", limit);
        int s = Math.min(shots.size(), Math.max(limit / 2, limit - actions.size()));
        int a = Math.min(actions.size(), limit - s);
        List<Moment> out = new java.util.ArrayList<>(actions.subList(0, a));
        out.addAll(shots.subList(0, s));
        return out;
    }

    // `where` and `order` are constants from the method above, never request input.
    private List<Moment> moments(int playerId, String where, String order, int limit) {
        return db.sql("""
            SELECT e.id AS event_id, e.match_id, e.type, e.second, e.x, e.y, e.end_x, e.end_y, e.bypassed, e.xg, e.goal,
                   ot.name AS opponent, to_char(m.kickoff, 'YYYY-MM-DD') AS kickoff, m.video_url
            FROM event e JOIN match m ON m.id = e.match_id
            JOIN team ot ON ot.id = CASE WHEN m.home_team_id = e.team_id THEN m.away_team_id ELSE m.home_team_id END
            WHERE e.player_id = :p AND %s ORDER BY %s LIMIT :n""".formatted(where, order))
            .param("p", playerId).param("n", limit).query(Moment.class).list();
    }
}
