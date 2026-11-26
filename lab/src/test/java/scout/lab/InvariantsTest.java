package scout.lab;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The rules in AGENTS.md that a reviewer would otherwise have to remember. Tests run from the repository root. */
class InvariantsTest {

    static List<Path> files(String dir, String suffix) throws Exception {
        try (Stream<Path> s = Files.walk(Path.of(dir))) {
            return s.filter(p -> p.toString().endsWith(suffix)).toList();
        }
    }

    @Test
    void whatServesNeverReadsTheTruth() throws Exception {
        for (String dir : List.of("core/src/main", "service/src/main")) {
            for (Path p : files(dir, ".java")) {
                String src = Files.readString(p);
                assertThat(src).as(p.toString()).doesNotContain("import scout.world").doesNotContain("import scout.lab");
            }
        }
        assertThat(Files.readString(Path.of("core/build.gradle.kts"))).doesNotContain("project(\":world\")").doesNotContain("project(\":lab\")");
        assertThat(Files.readString(Path.of("service/build.gradle.kts")).replaceAll("testImplementation\\(project\\([^)]*\\)\\)", ""))
            .doesNotContain("project(\":world\")").doesNotContain("project(\":lab\")");
    }

    @Test
    void sqlTakesValuesAsParametersOnly() throws Exception {
        // Every statement is a text block or literal with ? or :name placeholders; nothing is concatenated into SQL at run time
        // except the sort column, which SqlCompiler takes from the Metric enum.
        Pattern concat = Pattern.compile("\\.sql\\([^;]*\"\\s*\\+\\s*(?!CARD\\b)[a-z]");
        for (Path p : files("service/src/main/java", ".java")) assertThat(concat.matcher(Files.readString(p)).find()).as(p.toString()).isFalse();
        String compiler = Files.readString(Path.of("core/src/main/java/scout/core/SqlCompiler.java"));
        assertThat(compiler).contains(".column");
    }

    @Test
    void heldOutSeedsAndGatesAreUnchangedSinceTheHeldOutRun() throws Exception {
        Path lock = Path.of("reports/heldout.lock");
        if (!Files.exists(lock)) return;
        assertThat(Files.readString(lock)).contains("on config " + Cli.configHash());
    }

    @Test
    void theConsoleUsesTokensNotValues() throws Exception {
        Pattern hex = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
        for (Path p : files("web/src/app", ".ts")) assertThat(hex.matcher(Files.readString(p)).find()).as(p + " has a colour value; use a token").isFalse();
        assertThat(hex.matcher(Files.readString(Path.of("web/src/styles.scss"))).find()).isFalse();
    }

    @Test
    void everyPageThatLoadsDataHasItsLoadingErrorAndEmptyStates() throws Exception {
        for (Path p : files("web/src/app/features", "-page.ts")) {
            String src = Files.readString(p);
            if (!src.contains("httpResource")) continue;
            assertThat(src).as(p.toString()).contains("<app-loading").contains("<app-error").contains("<app-empty");
        }
    }
}
