# 3. The assistant's model proposes filters with quotes; code checks and decides

## Context
Letting a model write SQL or a filter object directly shows wrong players when it misreads a number, invents a
league or drops a word, and the scout cannot tell.

## Decision
The model returns constraints, each with the exact words it came from. Code rejects constraints whose quote is not in
the request, derives ages, minutes and the limit itself, resolves names through closed vocabularies and exact league
aliases, cross-checks the request for vocabulary terms the model dropped, and returns one of three outcomes: run,
ask one question, or say the request is outside the data. Word lists for vague and uncovered terms live in config.

## Consequences
In the lab (simulated model with set error rates, 300 requests per seed) wrong players shown fall from 44% to 0% and
correct answers rise from 56% to 90%, at the cost of asking back on 10% of requests. The vocabularies need upkeep
when a new league or metric is added.
