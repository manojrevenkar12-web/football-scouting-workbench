-- Football event data and the scouting read model. Event data arrives per match from the provider feed.
CREATE TABLE league (
    id        integer PRIMARY KEY,
    name      text    NOT NULL UNIQUE,
    country   text    NOT NULL
);

CREATE TABLE team (
    id        integer PRIMARY KEY,
    league_id integer NOT NULL REFERENCES league (id),
    name      text    NOT NULL
);

CREATE TABLE player (
    id        integer PRIMARY KEY,
    team_id   integer NOT NULL REFERENCES team (id),
    name      text    NOT NULL,
    position  text    NOT NULL CHECK (position IN ('GK', 'CB', 'FB', 'DM', 'CM', 'AM', 'W', 'ST')),
    age       smallint NOT NULL CHECK (age BETWEEN 14 AND 45),
    foot      text    NOT NULL CHECK (foot IN ('left', 'right'))
);

CREATE TABLE match (
    id           integer PRIMARY KEY,
    league_id    integer REFERENCES league (id),          -- NULL for cup matches between leagues
    home_team_id integer NOT NULL REFERENCES team (id),
    away_team_id integer NOT NULL REFERENCES team (id),
    round        smallint NOT NULL,
    kickoff      timestamp NOT NULL,
    competition  text    NOT NULL CHECK (competition IN ('league', 'cup')),
    video_url    text,
    CHECK (home_team_id <> away_team_id)
);

-- One row per player per match: the provider's totals, which the ratings are built from.
CREATE TABLE appearance (
    match_id         integer  NOT NULL REFERENCES match (id),
    player_id        integer  NOT NULL REFERENCES player (id),
    team_id          integer  NOT NULL REFERENCES team (id),
    opponent_team_id integer  NOT NULL REFERENCES team (id),
    from_minute      smallint NOT NULL,
    to_minute        smallint NOT NULL CHECK (to_minute >= from_minute),
    actions          integer  NOT NULL,
    bypassed         integer  NOT NULL,
    xg               numeric(6, 3) NOT NULL,
    shots            integer  NOT NULL,
    goals            integer  NOT NULL,
    defensive        integer  NOT NULL,
    PRIMARY KEY (match_id, player_id)
);

-- Every on-ball event, with where it happened and when in the match video.
CREATE TABLE event (
    id        bigint   PRIMARY KEY,
    match_id  integer  NOT NULL REFERENCES match (id),
    player_id integer  NOT NULL REFERENCES player (id),
    team_id   integer  NOT NULL REFERENCES team (id),
    type      text     NOT NULL CHECK (type IN ('PASS', 'CARRY', 'SHOT', 'DEFENSIVE')),
    second    integer  NOT NULL,
    x         real NOT NULL, y real NOT NULL, end_x real NOT NULL, end_y real NOT NULL,
    bypassed  smallint NOT NULL DEFAULT 0,
    xg        real     NOT NULL DEFAULT 0,
    goal      boolean  NOT NULL DEFAULT false
);

-- How hard each team is to play through, fitted across leagues (see OpponentModel).
CREATE TABLE team_defence (
    team_id  integer PRIMARY KEY REFERENCES team (id),
    defence  double precision NOT NULL,
    attack   double precision NOT NULL
);

-- The scouting read model: one row per player, rebuilt after each matchday. Searches read only this table.
CREATE TABLE player_ratings (
    player_id             integer PRIMARY KEY REFERENCES player (id),
    name                  text     NOT NULL,
    position              text     NOT NULL,
    age                   smallint NOT NULL,
    foot                  text     NOT NULL,
    team_id               integer  NOT NULL,
    team_name             text     NOT NULL,
    league_id             integer  NOT NULL,
    league_name           text     NOT NULL,
    minutes               integer  NOT NULL,
    actions               integer  NOT NULL,
    bypassed              integer  NOT NULL,
    progression           double precision NOT NULL,
    progression_per90_raw double precision NOT NULL,
    xg_per90              double precision NOT NULL,
    goals                 integer  NOT NULL,
    defensive_per90       double precision NOT NULL,
    refreshed_at          timestamp NOT NULL DEFAULT now()
);
CREATE INDEX player_ratings_position_progression ON player_ratings (position, progression DESC);
CREATE INDEX player_ratings_league ON player_ratings (league_id);
CREATE INDEX player_ratings_age ON player_ratings (age);
