-- Profiled in the lab (PerfStudy): per-player event lookups scanned the whole table without these.
CREATE INDEX event_player_type ON event (player_id, type) INCLUDE (bypassed, xg, goal, second, match_id);
CREATE INDEX event_match_second ON event (match_id, second);
CREATE INDEX appearance_player ON appearance (player_id) INCLUDE (from_minute, to_minute, actions, bypassed);
