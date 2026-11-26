# 4. One Angular console, served by the service jar

## Context
Scouts need a list, a player page and a request box; analysts need the lab numbers. Two deployments for one small
team is overhead.

## Decision
Angular 22 with standalone components, signals and `httpResource`, one folder per feature (`search`, `player`,
`assistant`, `lab`) and shared loading, error and empty components. `npm run build` output is copied into the jar's
static resources; Spring forwards the console's routes to `index.html`. Colours, type and spacing are tokens in one
SCSS file, with a dark scheme.

## Consequences
One artifact to deploy and one origin, so no CORS. The console must be built before `bootJar` for the jar to include
it; CI does that in the service job.
