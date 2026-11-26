package scout.service;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import scout.core.Assistant;
import scout.core.Settings;

@SpringBootApplication
public class ScoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScoutApplication.class, args);
    }

    @Bean
    Settings settings(@Value("${scout.root}") String root) {
        return Settings.load(Path.of(root));
    }

    /** Claude when an API key is configured; otherwise the vocabulary-and-grammar model, so the product works offline. */
    @Bean
    AssistantModels assistantModels(Settings s, @Value("${scout.anthropic-key}") String key) {
        Assistant.Claude claude = key == null || key.isBlank() ? null : new Assistant.Claude(key, s.assistant(), URI.create("https://api.anthropic.com/v1/messages"));
        return new AssistantModels(claude);
    }

    public record AssistantModels(Assistant.Claude claude) {}
}
