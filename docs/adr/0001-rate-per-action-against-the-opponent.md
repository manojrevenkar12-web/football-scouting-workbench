# 1. Rate line-breaking per action, against the opponent, shrunk to the position

## Context
Scouts sort by season totals or per-90 numbers. Totals reward minutes; per-90 rewards players in weaker leagues and
teams that have the ball more, and swings wildly for players with few minutes.

## Decision
Rate opponents bypassed per action. Fit each team's defensive strength from all appearances (alternating ridge least
squares on log rates, cup matches link the leagues) and credit each action against the opponent it was made against.
Pull players with few actions toward the position average with an empirical-Bayes prior worth 80 actions.

## Consequences
On dev seeds the rating finds 78% of the true top 20 young line-breakers against 48% for per-90 and 33% for totals,
and rates every player instead of dropping those under a minutes floor. Removing the opponent adjustment costs 26
points; removing shrinkage costs 3 points and 15% of players. The fit needs enough cross-league matches; with no cup
the league strengths are not identified.
