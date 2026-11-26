package scout.world;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Simulated leagues: teams, squads, a season of matches, who played how long, and every on-ball event.
 *
 * <p>The world knows the truth: each player's real progression, finishing and defending ability, and how strong every
 * league and team really is. Designs only get {@link #observed()}; only the scorer reads {@link #truth()}. Nothing here
 * imports the platform (:core) or reads config/platform.json.
 */
public final class World {

    public enum Position { GK, CB, FB, DM, CM, AM, W, ST }

    public enum EventType { PASS, CARRY, SHOT, DEFENSIVE }

    public record League(int id, String name, String country) {}

    public record Team(int id, int leagueId, String name) {}

    public record Player(int id, int teamId, String name, Position position, int age, String foot) {}

    public record Match(int id, Integer leagueId, int homeTeamId, int awayTeamId, int round, LocalDateTime kickoff, String competition) {}

    /** What a data provider records for one player in one match. */
    public record Appearance(int matchId, int playerId, int teamId, int opponentTeamId, int fromMinute, int toMinute,
                             int actions, int bypassed, double xg, int shots, int goals, int defensive) {
        public int minutes() { return toMinute - fromMinute; }
    }

    public record Event(long id, int matchId, int playerId, int teamId, EventType type, int second,
                        double x, double y, double endX, double endY, int bypassed, double xg, boolean goal) {}

    /** The hidden truth about a player. */
    public record Ability(double progression, double finishing, double defending) {}

    public record Observed(List<League> leagues, List<Team> teams, List<Player> players, List<Match> matches, List<Appearance> appearances) {}

    public record Truth(Map<Integer, Ability> abilities, Map<Integer, Double> leagueStrength, Map<Integer, Double> teamQuality) {}

    private static final String[] FIRST = {"Lukas", "Mats", "Joao", "Ruben", "Sander", "Milan", "Jonas", "Tiago", "Arne", "Kasper",
        "Noah", "Elias", "Diogo", "Bram", "Viktor", "Emil", "Leon", "Rafael", "Daan", "Oskar", "Felix", "Nuno", "Jesper", "Thijs"};
    private static final String[] LAST = {"Becker", "de Vries", "Almeida", "Peeters", "Nielsen", "Schmitt", "Jansen", "Costa", "Claes",
        "Holm", "Wagner", "Bakker", "Ferreira", "Maes", "Larsen", "Krause", "Visser", "Moreira", "Wouters", "Kristensen", "Vogel", "Smit"};
    private static final Map<Position, Double> ZONE_X = Map.of(Position.GK, 8.0, Position.CB, 26.0, Position.FB, 36.0,
        Position.DM, 42.0, Position.CM, 52.0, Position.AM, 66.0, Position.W, 70.0, Position.ST, 84.0);
    private static final Position[][] XI = {
        {Position.GK}, {Position.CB, Position.CB}, {Position.FB, Position.FB}, {Position.DM}, {Position.CM},
        {Position.AM}, {Position.W, Position.W}, {Position.ST}};

    public final long seed;
    public final WorldConfig cfg;
    private final List<League> leagues = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final List<Match> matches = new ArrayList<>();
    private final List<Appearance> appearances = new ArrayList<>();
    private final Map<Integer, Ability> abilities = new HashMap<>();
    private final Map<Integer, Double> leagueStrength = new HashMap<>();
    private final Map<Integer, Double> teamQuality = new HashMap<>();
    private final Map<Integer, Player> playerById = new HashMap<>();
    private final Map<Integer, Team> teamById = new HashMap<>();
    private final Map<Integer, List<Player>> squads = new HashMap<>();
    private final Map<Integer, List<Appearance>> byMatch = new HashMap<>();

    public World(long seed, WorldConfig cfg) {
        this.seed = seed;
        this.cfg = cfg;
        build();
    }

    public Observed observed() {
        return new Observed(List.copyOf(leagues), List.copyOf(teams), List.copyOf(players), List.copyOf(matches), List.copyOf(appearances));
    }

    public Truth truth() {
        return new Truth(Map.copyOf(abilities), Map.copyOf(leagueStrength), Map.copyOf(teamQuality));
    }

    // ---- building the season -------------------------------------------------------------------------------------

    private void build() {
        int teamId = 1, playerId = 1;
        for (WorldConfig.LeagueSpec ls : cfg.leagues()) {
            leagues.add(new League(ls.id(), ls.name(), ls.country()));
            leagueStrength.put(ls.id(), ls.strength());
            for (int t = 0; t < cfg.teamsPerLeague(); t++) {
                SplittableRandom r = Rng.stream(seed, "team", teamId);
                Team team = new Team(teamId, ls.id(), cityName(r) + (t % 3 == 0 ? " FC" : t % 3 == 1 ? " SV" : " United"));
                teams.add(team);
                teamById.put(teamId, team);
                teamQuality.put(teamId, cfg.teamQualitySd() * Rng.normal(r));
                List<Player> squad = new ArrayList<>();
                for (Map.Entry<String, WorldConfig.PositionSpec> e : orderedPositions()) {
                    for (int k = 0; k < e.getValue().share(); k++) {
                        SplittableRandom pr = Rng.stream(seed, "player", playerId);
                        int age = cfg.ageRange().get(0) + (int) Math.floor(Math.pow(pr.nextDouble(), 1.15) * (cfg.ageRange().get(1) - cfg.ageRange().get(0) + 1));
                        String foot = pr.nextDouble() < cfg.leftFootShare() ? "left" : "right";
                        Player p = new Player(playerId, teamId, FIRST[pr.nextInt(FIRST.length)] + " " + LAST[pr.nextInt(LAST.length)], Position.valueOf(e.getKey()), age, foot);
                        // Ability peaks in the mid-twenties; young players are on average a little behind.
                        double ageShift = -0.004 * Math.pow(age - 26, 2) + 0.05;
                        abilities.put(playerId, new Ability(cfg.abilitySd() * Rng.normal(pr) + ageShift, cfg.abilitySd() * Rng.normal(pr), cfg.abilitySd() * Rng.normal(pr)));
                        players.add(p);
                        playerById.put(playerId, p);
                        squad.add(p);
                        playerId++;
                    }
                }
                squads.put(teamId, squad);
                teamId++;
            }
        }
        int matchId = 1;
        LocalDate start = LocalDate.of(2026, 8, 7);
        for (League l : leagues) {
            List<Integer> ids = teams.stream().filter(t -> t.leagueId() == l.id()).map(Team::id).toList();
            List<int[]> fixtures = roundRobin(ids);
            for (int i = 0; i < fixtures.size(); i++) {
                int[] f = fixtures.get(i);
                int round = i / (ids.size() / 2) + 1;
                LocalDateTime ko = LocalDateTime.of(start.plusDays(7L * (round - 1) + (i % 3)), LocalTime.of(15 + (i % 4), 30));
                matches.add(new Match(matchId++, l.id(), f[0], f[1], round, ko, "league"));
            }
        }
        // Cup matches across leagues: the only games that tell how strong one league is against another.
        List<Integer> all = new ArrayList<>(teams.stream().map(Team::id).toList());
        for (int k = 0; k < cfg.crossLeagueMatchesPerTeam(); k++) {
            List<Integer> pool = new ArrayList<>(all);
            Collections.shuffle(pool, new java.util.Random(Rng.key(seed, "cup", k)));
            for (int i = 0; i + 1 < pool.size(); i += 2) {
                int a = pool.get(i), b = pool.get(i + 1);
                if (teamById.get(a).leagueId() == teamById.get(b).leagueId()) {
                    for (int j = i + 2; j < pool.size(); j++) {
                        if (teamById.get(pool.get(j)).leagueId() != teamById.get(a).leagueId()) {
                            Collections.swap(pool, i + 1, j);
                            break;
                        }
                    }
                    b = pool.get(i + 1);
                }
                if (teamById.get(a).leagueId() == teamById.get(b).leagueId()) continue;
                LocalDateTime ko = LocalDateTime.of(start.plusDays(17L + 28L * k + (i % 2)), LocalTime.of(20, 45));
                matches.add(new Match(matchId++, null, a, b, 0, ko, "cup"));
            }
        }
        for (Match m : matches) {
            for (boolean home : new boolean[] {true, false}) {
                int own = home ? m.homeTeamId() : m.awayTeamId(), opp = home ? m.awayTeamId() : m.homeTeamId();
                lineup(m, own).forEach((pid, span) -> {
                    Appearance ap = play(m, pid, own, opp, span[0], span[1], null);
                    appearances.add(ap);
                    byMatch.computeIfAbsent(m.id(), k -> new ArrayList<>()).add(ap);
                });
            }
        }
    }

    private List<Map.Entry<String, WorldConfig.PositionSpec>> orderedPositions() {
        List<Map.Entry<String, WorldConfig.PositionSpec>> out = new ArrayList<>(cfg.positions().entrySet());
        out.sort(Comparator.comparing(e -> Position.valueOf(e.getKey()).ordinal()));
        return out;
    }

    private static String cityName(SplittableRandom r) {
        String[] a = {"Nord", "Sud", "West", "Ost", "Alt", "Neu", "Hoch", "Ober", "Unter", "Mittel"};
        String[] b = {"hafen", "berg", "feld", "dorf", "stadt", "brück", "heim", "bach", "burg", "au"};
        return a[r.nextInt(a.length)] + b[r.nextInt(b.length)];
    }

    static List<int[]> roundRobin(List<Integer> ids) {
        List<Integer> rot = new ArrayList<>(ids);
        int n = rot.size();
        List<int[]> first = new ArrayList<>();
        for (int round = 0; round < n - 1; round++) {
            for (int i = 0; i < n / 2; i++) {
                int a = rot.get(i), b = rot.get(n - 1 - i);
                first.add(round % 2 == 0 ? new int[] {a, b} : new int[] {b, a});
            }
            rot.add(1, rot.remove(n - 1));
        }
        List<int[]> out = new ArrayList<>(first);
        for (int[] f : first) out.add(new int[] {f[1], f[0]});
        return out;
    }

    /** Who plays in a match and from which minute to which: the better players start more often. */
    private Map<Integer, int[]> lineup(Match m, int teamId) {
        SplittableRandom r = Rng.stream(seed, "lineup", m.id(), teamId);
        Map<Integer, int[]> out = new java.util.LinkedHashMap<>();
        List<Player> squad = squads.get(teamId);
        List<Player> bench = new ArrayList<>();
        List<Player> starters = new ArrayList<>();
        for (Position[] slot : XI) {
            Position pos = slot[0];
            List<Player> cands = new ArrayList<>(squad.stream().filter(p -> p.position() == pos).toList());
            cands.removeIf(p -> r.nextDouble() < cfg.minutes().injuryRate());
            cands.sort(Comparator.comparingDouble(p -> -(selection(p) + 0.15 * Rng.normal(r))));
            for (int k = 0; k < cands.size(); k++) (k < slot.length ? starters : bench).add(cands.get(k));
        }
        for (Player p : starters) out.put(p.id(), new int[] {0, 90});
        int subs = 0;
        for (Player b : bench) {
            if (subs == 3 || r.nextDouble() > cfg.minutes().rotationShare()) continue;
            Player off = starters.stream().filter(s -> s.position() == b.position() && out.get(s.id())[1] == 90).findFirst().orElse(null);
            if (off == null) continue;
            int minute = 55 + r.nextInt(30);
            out.get(off.id())[1] = minute;
            out.put(b.id(), new int[] {minute, 90});
            subs++;
        }
        return out;
    }

    private double selection(Player p) {
        Ability a = abilities.get(p.id());
        return a.progression() + a.finishing() + a.defending();
    }

    private double strength(int teamId) {
        return leagueStrength.get(teamById.get(teamId).leagueId()) + teamQuality.get(teamId);
    }

    /** Plays one appearance. With a sink, it also emits every event; the totals are the same either way. */
    private Appearance play(Match m, int playerId, int own, int opp, int from, int to, List<Event> sink) {
        Player p = playerById.get(playerId);
        WorldConfig.PositionSpec ps = cfg.positions().get(p.position().name());
        Ability a = abilities.get(playerId);
        SplittableRandom r = Rng.stream(seed, "appearance", m.id(), playerId);
        double frac = (to - from) / 90.0;
        double balance = strength(own) - strength(opp);
        int actions = Rng.poisson(r, frac * ps.actionsPer90() * Math.exp(cfg.possessionEffect() * balance / 2));
        double mu = ps.bypassPerAction() * Math.exp(a.progression() - cfg.opponentEffect() * strength(opp));
        int bypassed = 0;
        Position pos = p.position();
        for (int i = 0; i < actions; i++) {
            int b = Rng.poisson(r, mu);
            bypassed += b;
            int sec = from * 60 + r.nextInt(Math.max(1, (to - from) * 60));
            double x = clamp(ZONE_X.get(pos) + 9 * Rng.normal(r), 1, 104), y = clamp(34 + 22 * Rng.normal(r), 1, 67);
            double ex = clamp(x + 4 + 7 * b + 5 * Rng.normal(r), 1, 104), ey = clamp(y + 10 * Rng.normal(r), 1, 67);
            boolean carry = r.nextDouble() < 0.25;
            if (sink != null) sink.add(new Event(0, m.id(), playerId, own, carry ? EventType.CARRY : EventType.PASS, sec, x, y, ex, ey, b, 0, false));
        }
        double xgRate = frac * ps.xgPer90() * Math.exp(a.finishing() - 0.6 * strength(opp));
        int shots = Rng.poisson(r, xgRate / 0.11);
        double xg = 0;
        int goals = 0;
        for (int i = 0; i < shots; i++) {
            double q = Math.min(0.85, 0.03 + 0.16 * Math.pow(r.nextDouble(), 2.2) * 2);
            boolean goal = r.nextDouble() < Math.min(0.95, q * Math.exp(a.finishing()));
            xg += q;
            goals += goal ? 1 : 0;
            int sec = from * 60 + r.nextInt(Math.max(1, (to - from) * 60));
            double x = clamp(88 + 8 * r.nextDouble(), 80, 104), y = clamp(34 + 9 * Rng.normal(r), 10, 58);
            if (sink != null) sink.add(new Event(0, m.id(), playerId, own, EventType.SHOT, sec, x, y, 105, 34, 0, round3(q), goal));
        }
        int defensive = Rng.poisson(r, frac * ps.defPer90() * Math.exp(a.defending() - 0.5 * balance));
        for (int i = 0; i < defensive; i++) {
            int sec = from * 60 + r.nextInt(Math.max(1, (to - from) * 60));
            double x = clamp(105 - ZONE_X.get(pos) - 30 + 12 * Rng.normal(r) + 30, 1, 104), y = clamp(34 + 20 * Rng.normal(r), 1, 67);
            if (sink != null) sink.add(new Event(0, m.id(), playerId, own, EventType.DEFENSIVE, sec, x, y, x, y, 0, 0, false));
        }
        return new Appearance(m.id(), playerId, own, opp, from, to, actions, bypassed, round3(xg), shots, goals, defensive);
    }

    /** Every event of one match, in time order, regenerated from the same streams as the totals. */
    public List<Event> events(int matchId) {
        Match m = matches.get(matchId - 1);
        List<Event> out = new ArrayList<>();
        for (Appearance ap : byMatch.getOrDefault(matchId, List.of())) {
            play(m, ap.playerId(), ap.teamId(), ap.opponentTeamId(), ap.fromMinute(), ap.toMinute(), out);
        }
        out.sort(Comparator.comparingInt(Event::second).thenComparingInt(Event::playerId));
        List<Event> numbered = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) {
            Event e = out.get(i);
            numbered.add(new Event((long) matchId * 10_000 + i, e.matchId(), e.playerId(), e.teamId(), e.type(), e.second(), e.x(), e.y(), e.endX(), e.endY(), e.bypassed(), e.xg(), e.goal()));
        }
        return numbered;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, Math.round(v * 10) / 10.0));
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
