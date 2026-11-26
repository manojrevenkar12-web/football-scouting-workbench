# Working in football-scouting-workbench

Fast feedback first: `./gradlew build` compiles every module and runs every test (world, core, lab, the Spring
service; the service's PostgreSQL tests run when `SCOUT_TEST_DB_URL` points at an empty database). `cd web && npm run
build` for the console. After a change to the ratings, the assistant, the world or any settings run
`./gradlew :lab:run --args=dev` then `--args=report` and commit `reports/dev.json`, `README.md`, `REPORT.md` and
`web/public/data/results.json`; CI fails when they disagree with the code.

## Modules

| module | what it is | may depend on |
|---|---|---|
| `world` | the simulated leagues, players, matches and events, with the truth | nothing |
| `core` | ratings, opponent model, query and SQL compiler, similarity, assistant checks | nothing |
| `service` | Spring Boot 4: REST, Flyway, JdbcClient on PostgreSQL; serves the console | `core` |
| `lab` | runs the designs on the world, the perf study, the reports, the loader | `core`, `world` |
| `web` | Angular 22 console | the REST API |

## Invariants (checked in `lab/src/test/java/scout/lab/InvariantsTest.java`, `WorldTest` and the tests)

1. **The truth stays in the world.** `core` and `service` never import `world` or `lab`; the Gradle graph makes it
   impossible and the invariants test reads the sources. The database holds only what a club would receive: matches,
   appearances, events.
2. **The world does not read the platform's settings.** `world` reads `config/lab/world.json`, never
   `config/platform.json` or the gates.
3. **Same luck for every design.** Every random draw is keyed by (seed, purpose, ids) in `world/Rng.java`, so all
   designs rate the same season.
4. **The model reads and code decides.** The assistant's model proposes constraints with quotes; code checks every
   quote, derives every number, resolves every name through a closed vocabulary and decides whether to run, ask or
   refuse. Request text is data: it is fenced in the prompt and never reaches SQL.
5. **Values never enter SQL.** Every statement takes parameters; the only text built at run time is the sort column,
   taken from the `Metric` enum. The SQL search and the in-memory search must return the same players.
6. **Searches read the read model.** `player_ratings` is replaced in one transaction by the refresh; nothing on the
   request path aggregates events except one player's moments, which use the V2 index.
7. **Settings in config.** Rating constants, limits, model name and the assistant's word lists live in
   `config/platform.json`; the console's colours, sizes and durations live in `web/src/styles/_tokens.scss` (a test
   rejects colour values in components). Every data view has loading, error and empty states (tested).
8. **Generated reports.** README numbers, `REPORT.md` and the console's lab page come from `reports/`; never edit them
   by hand.

## The held-out rule

`config/lab/seeds.json` lists held-out seeds. `./gradlew :lab:run --args=heldout` runs them once with the code and
settings dev ended on, writes `reports/heldout.json` and `reports/heldout.lock` (with a hash of the lab and platform
config), and refuses to run again. A test fails if the config changes after the lock. Findings go into
`docs/heldout.md`. Fixes are made and measured on dev seeds; the held-out seeds and `config/lab/gates.json` are not
moved.
