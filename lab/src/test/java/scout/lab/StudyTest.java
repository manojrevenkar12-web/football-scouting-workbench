package scout.lab;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import scout.core.Settings;
import scout.world.World;
import scout.world.WorldConfig;

class StudyTest {
    static final Settings S = Settings.load(Path.of("."));
    static final WorldConfig CFG = WorldConfig.load(Path.of("."));

    @Test
    void thePlatformFindsMoreRealTalentThanTheDefaultSorts() {
        var r = RankingStudy.run(new World(42, CFG), S);
        assertThat(r.get("platform").get("precisionAt20")).isGreaterThan(r.get("per90").get("precisionAt20"));
        assertThat(r.get("platform").get("precisionAt20")).isGreaterThan(r.get("totals").get("precisionAt20"));
        assertThat(r.get("platform").get("covered")).isEqualTo(1.0);
    }

    @Test
    void theAssistantNeverShowsWrongPlayersAndTheUncheckedModelDoes() {
        var a = AssistantStudy.run(new World(42, CFG), S);
        assertThat(a.get("assistant").get("wrongShown")).isZero();
        assertThat(a.get("model_unchecked").get("wrongShown")).isGreaterThan(0.2);
    }

    @Test
    void gatesCompareWithAValueOrAnotherDesign() throws Exception {
        var res = Cli.JSON.readTree("""
            {"ranking": {"platform": {"precisionAt20": 0.8, "spearman": 0.95, "covered": 1.0}, "per90": {"precisionAt20": 0.6}},
             "assistant": {"assistant": {"wrongShown": 0, "right": 0.9, "asked": 0.1}}}""");
        var g = Report.gates(res);
        assertThat(g).filteredOn(x -> !(Boolean) x.get("pass")).extracting(x -> x.get("id")).containsExactly("beats_per90");
    }
}
