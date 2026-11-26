# Repeatable tasks

**Add a league.** Add it with its strength to `leagues` in `config/lab/world.json` (the world) and its names to
`leagueAliases` in `config/platform.json` (the assistant). Remove it from `uncoveredLeagues` if it was listed. Run
`dev`, `report`; the held-out lock will fail, which is right: record in `docs/heldout.md` that held-out numbers
predate the change.

**Add a rating a scout can sort by.** Add the column to `player_ratings` in a new Flyway migration (never edit V1/V2),
compute it in `core/Ratings.java`, add a `Metric` constant with its column, add it to `METRICS` in
`web/src/app/core/api.ts`, and add its words to the assistant's metric vocabulary. Extend `QueryTest` so the SQL and
in-memory searches agree on it.

**Tune the rating.** Change `shrinkage.priorActions` or `opponentModel` in `config/platform.json`, run `dev` and
compare `platform` with its ablations in `reports/dev.json`. Decide on dev seeds only.

**Profile a search.** `./gradlew :lab:run --args=perf` against a scratch PostgreSQL (`SCOUT_DB_URL`) loads a season
into the `perf_lab` schema, times the designs and keeps the top plan nodes in `reports/perf.json`. Add an index as a
new migration and a design to `PerfStudy` to measure it.

**Teach the assistant a word.** Positions, feet and metrics live in `core/Assistant.java` (closed vocabularies);
vague age words and uncovered leagues live in `config/platform.json`. Add a case to `AssistantTest` and run `dev` to
see the study move.

**Try Claude.** Set `ANTHROPIC_API_KEY` and start the service; `/api/assistant` answers with `"source": "claude"` and
its proposals pass the same checks. The lab keeps the simulated model so results stay comparable.

**Refresh the screenshots.** Start the jar with `load-db 77` data, then `python3 scripts/screenshots.py`.
