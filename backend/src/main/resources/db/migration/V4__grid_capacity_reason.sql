-- A matched session that Edmonds-Karp grants 0 W cannot charge, so its booking is WAITLISTED with
-- INSUFFICIENT_GRID_CAPACITY instead of being ASSIGNED at 0 kW. Mirrors com.chargewise.entity.StatusReason.
ALTER TABLE bookings DROP CONSTRAINT bookings_status_reason_check;

ALTER TABLE bookings ADD CONSTRAINT bookings_status_reason_check
    CHECK (status_reason IN ('NO_COMPATIBLE_CONNECTOR_TYPE', 'STATION_NOT_OPERATIONAL',
                             'CONNECTORS_OUT_OF_SERVICE', 'CONNECTORS_BOOKED_IN_WINDOW',
                             'INSUFFICIENT_CONNECTOR_POWER', 'LOST_CONTENTION', 'WINDOW_EXPIRED',
                             'ENERGY_EXCEEDS_BATTERY_HEADROOM', 'CONNECTOR_OUTAGE', 'STATION_OUTAGE',
                             'INSUFFICIENT_GRID_CAPACITY'));
