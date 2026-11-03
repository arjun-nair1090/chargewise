-- Baseline: btree_gist lets EXCLUDE constraints combine equality on connector/vehicle ids with
-- tstzrange overlap, which V3 uses to make overlapping reservations impossible (architecture D6).
CREATE EXTENSION IF NOT EXISTS btree_gist;
