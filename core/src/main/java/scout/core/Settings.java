package scout.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** config/platform.json: every tuned number the platform uses. */
public record Settings(Shrinkage shrinkage, OpponentModel opponentModel, Query query, Similarity similarity, Assistant assistant, Map<String, Integer> leagueAliases) {

    public record Shrinkage(double priorActions) {}

    public record OpponentModel(int iterations, double ridge) {}

    public record Query(int maxLimit, int defaultLimit, int minMinutesDefault, int maxAge) {}

    public record Similarity(int minMinutes, int neighbours) {}

    public record Assistant(String model, int maxTokens, int timeoutSeconds, java.util.List<String> vagueAgeWords, java.util.List<String> uncoveredLeagues) {}

    public static final JsonMapper JSON = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public static Settings load(Path root) {
        try {
            return JSON.readValue(Files.readString(root.resolve("config/platform.json")), Settings.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read config/platform.json", e);
        }
    }
}
