-- The network spans India, so each station records the city and the state or union territory it is in. Both are
-- nullable: an operator can add a station before its place is known.
ALTER TABLE stations ADD COLUMN city VARCHAR(80);
ALTER TABLE stations ADD COLUMN state VARCHAR(60);

CREATE INDEX stations_state_city_idx ON stations (state, city);
