package scout.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import scout.core.Catalog;
import scout.core.Data;
import scout.core.OpponentModel;
import scout.core.Ratings;
import scout.core.Settings;

/** Rebuilds the ratings read model from the appearances: after each matchday, or on start when it is empty. */
@Service
public class RatingsService {
    private static final Logger log = LoggerFactory.getLogger(RatingsService.class);
    private final ScoutRepository repo;
    private final Settings settings;
    private final boolean refreshOnStart;
    private volatile Catalog catalog;

    public RatingsService(ScoutRepository repo, Settings settings, @Value("${scout.refresh-on-start}") boolean refreshOnStart) {
        this.repo = repo;
        this.settings = settings;
        this.refreshOnStart = refreshOnStart;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (refreshOnStart && repo.ratedPlayers() == 0 && !repo.leagues().isEmpty()) refresh();
    }

    public synchronized int refresh() {
        long t = System.currentTimeMillis();
        List<Data.AppearanceRow> apps = repo.appearances();
        Map<Integer, Data.TeamRow> teams = repo.teams();
        OpponentModel opp = OpponentModel.fit(apps, settings.opponentModel());
        List<Ratings.Rating> rs = Ratings.compute(repo.players(), teams, apps, opp, settings.shrinkage(), Ratings.Options.PLATFORM);
        Map<Integer, String> names = repo.leagues().stream().collect(Collectors.toMap(Data.LeagueRow::id, Data.LeagueRow::name));
        repo.replaceRatings(rs, opp, teams, names);
        catalog = null;
        log.info("rated {} players from {} appearances in {} ms", rs.size(), apps.size(), System.currentTimeMillis() - t);
        return rs.size();
    }

    public Catalog catalog() {
        Catalog c = catalog;
        if (c == null) {
            c = new Catalog(repo.leagues(), settings.leagueAliases());
            catalog = c;
        }
        return c;
    }
}
