-- Overlap safeguards (architecture D6). Windows are half-open tstzrange '[)', so back-to-back bookings
-- ([10:00,11:00) then [11:00,12:00)) are allowed while any real overlap is rejected by the database,
-- even if the allocation service were bypassed. btree_gist (V1) supplies the gist "=" operator on BIGINT.

-- One connector never carries two ASSIGNED bookings whose windows overlap.
ALTER TABLE bookings ADD CONSTRAINT bookings_no_connector_overlap
    EXCLUDE USING gist (connector_id WITH =,
                        tstzrange(requested_start, requested_end, '[)') WITH &&)
    WHERE (status = 'ASSIGNED' AND connector_id IS NOT NULL);

-- One vehicle never holds two active (pending, waitlisted or assigned) bookings whose windows overlap.
ALTER TABLE bookings ADD CONSTRAINT bookings_no_vehicle_overlap
    EXCLUDE USING gist (vehicle_id WITH =,
                        tstzrange(requested_start, requested_end, '[)') WITH &&)
    WHERE (status IN ('PENDING', 'WAITLISTED', 'ASSIGNED'));
