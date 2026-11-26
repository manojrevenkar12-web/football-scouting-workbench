# 2. Searches read a read model, refreshed in one transaction

## Context
Ratings need every event of every player. Computing them per search means aggregating about two million rows.

## Decision
Keep the facts (match, appearance, event) as written, compute ratings in `core` on refresh and replace
`player_ratings` in one transaction. Searches and the player page read `player_ratings`; only a player's moments
read `event`, through the `(player_id, type)` index.

## Consequences
Measured on PostgreSQL 16 with 1.95 million events: aggregating events per search, p95 240 ms; with the V2 indexes,
24 ms; reading the read model, 0.3 ms. Ratings are as fresh as the last refresh, which suits a scouting workflow that
loads matches in batches. The refresh is behind an admin key.
