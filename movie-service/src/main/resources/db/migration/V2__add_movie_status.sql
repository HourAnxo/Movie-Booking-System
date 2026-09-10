-- Lets the catalogue distinguish what is on now from what is upcoming,
-- which is what /api/movies/status/{status} filters on.
ALTER TABLE movies
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'NOW_SHOWING' AFTER poster_url;
