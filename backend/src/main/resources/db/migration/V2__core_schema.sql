-- Core schema: the spec §9 tables plus the architecture D6 additions (allocation_runs, benchmark_runs,
-- booking assignment metadata). Power is kW NUMERIC(8,2) and energy kWh NUMERIC(8,2) (D1); every
-- timestamp is TIMESTAMPTZ in UTC (D2). CHECK lists mirror the Java enums in com.chargewise.entity.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('DRIVER', 'OPERATOR')),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE vehicles (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES users (id),
    registration_number  VARCHAR(40)  NOT NULL UNIQUE,
    battery_capacity_kwh NUMERIC(8,2) NOT NULL CHECK (battery_capacity_kwh > 0),
    current_soc          NUMERIC(5,2) NOT NULL CHECK (current_soc BETWEEN 0 AND 100),
    max_charging_kw      NUMERIC(8,2) NOT NULL CHECK (max_charging_kw > 0),
    created_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_vehicles_user ON vehicles (user_id);

CREATE TABLE vehicle_connector_types (
    vehicle_id     BIGINT      NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    connector_type VARCHAR(40) NOT NULL
        CHECK (connector_type IN ('CCS2', 'TYPE2_AC', 'CHADEMO', 'BHARAT_DC001', 'BHARAT_AC001')),
    PRIMARY KEY (vehicle_id, connector_type)
);

CREATE TABLE stations (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(160)  NOT NULL,
    address          TEXT          NOT NULL,
    latitude         NUMERIC(10,7) CHECK (latitude BETWEEN -90 AND 90),
    longitude        NUMERIC(10,7) CHECK (longitude BETWEEN -180 AND 180),
    grid_capacity_kw NUMERIC(10,2) NOT NULL CHECK (grid_capacity_kw >= 0),
    status           VARCHAR(24)   NOT NULL CHECK (status IN ('OPERATIONAL', 'MAINTENANCE', 'OFFLINE')),
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_stations_status ON stations (status);

CREATE TABLE connectors (
    id             BIGSERIAL PRIMARY KEY,
    station_id     BIGINT       NOT NULL REFERENCES stations (id),
    connector_type VARCHAR(40)  NOT NULL
        CHECK (connector_type IN ('CCS2', 'TYPE2_AC', 'CHADEMO', 'BHARAT_DC001', 'BHARAT_AC001')),
    max_power_kw   NUMERIC(8,2) NOT NULL CHECK (max_power_kw > 0),
    status         VARCHAR(24)  NOT NULL CHECK (status IN ('OPERATIONAL', 'OUT_OF_SERVICE'))
);

CREATE INDEX idx_connectors_station_status ON connectors (station_id, status);

CREATE TABLE allocation_runs (
    id             BIGSERIAL PRIMARY KEY,
    interval_start TIMESTAMPTZ NOT NULL,
    interval_end   TIMESTAMPTZ NOT NULL,
    created_by     BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    result         JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    CHECK (interval_end > interval_start)
);

CREATE TABLE bookings (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES users (id),
    vehicle_id           BIGINT       NOT NULL REFERENCES vehicles (id),
    station_id           BIGINT       NOT NULL REFERENCES stations (id),
    connector_id         BIGINT       REFERENCES connectors (id),
    requested_energy_kwh NUMERIC(8,2) NOT NULL CHECK (requested_energy_kwh > 0),
    requested_start      TIMESTAMPTZ  NOT NULL,
    requested_end        TIMESTAMPTZ  NOT NULL,
    status               VARCHAR(24)  NOT NULL
        CHECK (status IN ('PENDING', 'ASSIGNED', 'WAITLISTED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    status_reason        VARCHAR(40)
        CHECK (status_reason IN ('NO_COMPATIBLE_CONNECTOR_TYPE', 'STATION_NOT_OPERATIONAL',
                                 'CONNECTORS_OUT_OF_SERVICE', 'CONNECTORS_BOOKED_IN_WINDOW',
                                 'INSUFFICIENT_CONNECTOR_POWER', 'LOST_CONTENTION', 'WINDOW_EXPIRED',
                                 'ENERGY_EXCEEDS_BATTERY_HEADROOM', 'CONNECTOR_OUTAGE', 'STATION_OUTAGE')),
    assigned_at          TIMESTAMPTZ,
    allocation_run_id    BIGINT       REFERENCES allocation_runs (id),
    created_at           TIMESTAMPTZ  NOT NULL,
    CHECK (requested_end > requested_start),
    CHECK (status <> 'ASSIGNED' OR connector_id IS NOT NULL)
);

CREATE INDEX idx_bookings_status_start ON bookings (status, requested_start);
CREATE INDEX idx_bookings_user ON bookings (user_id);

CREATE TABLE charging_sessions (
    id                   BIGSERIAL PRIMARY KEY,
    booking_id           BIGINT        NOT NULL UNIQUE REFERENCES bookings (id),
    station_id           BIGINT        NOT NULL REFERENCES stations (id),
    connector_id         BIGINT        NOT NULL REFERENCES connectors (id),
    start_time           TIMESTAMPTZ,
    end_time             TIMESTAMPTZ,
    energy_delivered_kwh NUMERIC(10,3) NOT NULL DEFAULT 0 CHECK (energy_delivered_kwh >= 0),
    allocated_power_kw   NUMERIC(8,2)  NOT NULL DEFAULT 0 CHECK (allocated_power_kw >= 0),
    status               VARCHAR(24)   NOT NULL
        CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'INTERRUPTED')),
    CHECK (start_time IS NULL OR end_time IS NULL OR end_time >= start_time)
);

CREATE INDEX idx_sessions_station_status ON charging_sessions (station_id, status);

CREATE TABLE power_allocations (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT       NOT NULL REFERENCES charging_sessions (id) ON DELETE CASCADE,
    requested_kw    NUMERIC(8,2) NOT NULL CHECK (requested_kw >= 0),
    allocated_kw    NUMERIC(8,2) NOT NULL CHECK (allocated_kw >= 0),
    allocation_time TIMESTAMPTZ  NOT NULL,
    CHECK (allocated_kw <= requested_kw)
);

CREATE INDEX idx_power_allocations_session ON power_allocations (session_id);

CREATE TABLE simulations (
    id            BIGSERIAL PRIMARY KEY,
    created_by    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    name          VARCHAR(160) NOT NULL,
    random_seed   BIGINT       NOT NULL,
    configuration JSONB        NOT NULL,
    status        VARCHAR(24)  NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE simulation_results (
    id                BIGSERIAL PRIMARY KEY,
    simulation_id     BIGINT      NOT NULL REFERENCES simulations (id) ON DELETE CASCADE,
    algorithm_name    VARCHAR(80) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    input_summary     JSONB       NOT NULL,
    output_summary    JSONB       NOT NULL,
    metrics           JSONB       NOT NULL,
    runtime_ns        BIGINT      CHECK (runtime_ns >= 0),
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_simulation_results_simulation ON simulation_results (simulation_id);

CREATE TABLE benchmark_runs (
    id            BIGSERIAL PRIMARY KEY,
    created_by    BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    random_seed   BIGINT      NOT NULL,
    configuration JSONB       NOT NULL,
    results       JSONB,
    status        VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    created_at    TIMESTAMPTZ NOT NULL
);
