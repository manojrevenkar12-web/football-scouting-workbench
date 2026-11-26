package scout.world;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** config/lab/world.json. */
public record WorldConfig(
        List<LeagueSpec> leagues,
        int teamsPerLeague,
        int squadSize,
        double teamQualitySd,
        int crossLeagueMatchesPerTeam,
        Map<String, PositionSpec> positions,
        double abilitySd,
        double opponentEffect,
        double possessionEffect,
        List<Integer> ageRange,
        double leftFootShare,
        Minutes minutes,
        int eventsPerMatchCap,
        Clip videoClip,
        ModelErrors model) {

    public record LeagueSpec(int id, String name, String country, double strength) {}

    public record PositionSpec(int share, double actionsPer90, double bypassPerAction, double xgPer90, double defPer90) {}

    public record Minutes(double starterShare, double rotationShare, double injuryRate) {}

    public record Clip(int before, int after) {}

    public record ModelErrors(double leagueNameError, double ageOffByOne, double footDropped, double unknownMetric, double sqlInjectionText, double ambiguousShare) {}

    public static WorldConfig load(Path root) {
        try {
            JsonMapper m = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
            return m.readValue(Files.readString(root.resolve("config/lab/world.json")), WorldConfig.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read config/lab/world.json", e);
        }
    }
}
