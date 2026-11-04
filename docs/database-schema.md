# Database Schema

PostgreSQL 18 database with Flyway migrations in `backend/src/main/resources/db/migration/`. The design
decisions are recorded in [architecture.md §8 (D6)](architecture.md). Hibernate runs with
`ddl-auto: validate`, so the application refuses to start if an entity disagrees with the migrated
schema.

## Units and time

- Power is stored in kW and energy in kWh. Both use `NUMERIC` with 2 decimal places, except
  `energy_delivered_kwh`, which uses 3.
- Inside the algorithms these values become integer watts and watt-hours, and the conversion is exact
  (D1).
- Timestamps are `TIMESTAMPTZ` in UTC.
- Booking windows are half-open: `[requested_start, requested_end)` (D2).

## Migration history

| Version | File | Contents |
|---|---|---|
| V1 | `V1__baseline.sql` | `CREATE EXTENSION btree_gist`, which lets `EXCLUDE` constraints combine `=` on `BIGINT` with range overlap |
| V2 | `V2__core_schema.sql` | All tables, `CHECK` constraints, foreign keys and indexes |
| V3 | `V3__exclusion_constraints.sql` | The two overlap exclusion constraints |
| V4 | `V4__grid_capacity_reason.sql` | Adds `INSUFFICIENT_GRID_CAPACITY` to the `bookings.status_reason` CHECK: a matched request granted 0 W is waitlisted, not assigned at 0 kW |
| V5 | `V5__station_city_state.sql` | Adds nullable `stations.city` and `stations.state` and the `stations_state_city_idx` index, for the India-wide network |

## Tables

The spec §9 tables are used. Bold text marks additions or tightenings relative to the spec.

### users

| Column | Type | Constraints |
|---|---|---|
| id | BIGSERIAL | PK |
| name | VARCHAR(120) | NOT NULL |
| email | VARCHAR(255) | NOT NULL, UNIQUE |
| password_hash | VARCHAR(255) | NOT NULL (BCrypt, cost 12) |
| role | VARCHAR(20) | NOT NULL, **CHECK IN ('DRIVER','OPERATOR')** |
| created_at | TIMESTAMPTZ | NOT NULL |

### vehicles

| Column | Type | Constraints |
|---|---|---|
| id | BIGSERIAL | PK |
| user_id | BIGINT | **NOT NULL**, FK users |
| registration_number | VARCHAR(40) | NOT NULL, UNIQUE |
| battery_capacity_kwh | NUMERIC(8,2) | NOT NULL, **CHECK > 0** |
| current_soc | NUMERIC(5,2) | NOT NULL, **CHECK BETWEEN 0 AND 100** |
| max_charging_kw | NUMERIC(8,2) | NOT NULL, **CHECK > 0** |
| created_at | TIMESTAMPTZ | NOT NULL |

### vehicle_connector_types

Primary key `(vehicle_id, connector_type)`.

| Column | Type | Constraints |
|---|---|---|
| vehicle_id | BIGINT | FK vehicles, **ON DELETE CASCADE** |
| connector_type | VARCHAR(40) | **CHECK IN (CCS2, TYPE2_AC, CHADEMO, BHARAT_DC001, BHARAT_AC001)** |

### stations

| Column | Type | Constraints |
|---|---|---|
| id | BIGSERIAL | PK |
| name | VARCHAR(160) | NOT NULL |
| address | TEXT | NOT NULL |
| city | VARCHAR(80) | NULL (V5); null for a station created without one |
| state | VARCHAR(60) | NULL (V5); state or union territory |
| latitude / longitude | NUMERIC(10,7) | **CHECK within ±90 / ±180** |
| grid_capacity_kw | NUMERIC(10,2) | NOT NULL, **CHECK ≥ 0** |
| status | VARCHAR(24) | NOT NULL, **CHECK IN (OPERATIONAL, MAINTENANCE, OFFLINE)** |
| created_at | TIMESTAMPTZ | NOT NULL |

### connectors

Occupancy is never stored here; it is derived from bookings.

| Column | Type | Constraints |
|---|---|---|
| id | BIGSERIAL | PK |
| station_id | BIGINT | **NOT NULL**, FK stations |
| connector_type | VARCHAR(40) | NOT NULL, CHECK as above |
| max_power_kw | NUMERIC(8,2) | NOT NULL, **CHECK > 0** |
| status | VARCHAR(24) | NOT NULL, **CHECK IN (OPERATIONAL, OUT_OF_SERVICE)** |

### allocation_runs (D6 addition)

| Column | Type | Constraints |
|---|---|---|
| id | BIGSERIAL | PK |
| interval_start, interval_end | TIMESTAMPTZ | NOT NULL, CHECK end > start |
| created_by | BIGINT | FK users, ON DELETE SET NULL |
| result | JSONB | NOT NULL: assignments, reasons, power allocations, bottlenecks, percentiles, runtimes |
| created_at | TIMESTAMPTZ | NOT NULL |

### bookings

| Column | Type | Constraints |
|---|---|---|
| id | BIGSERIAL | PK |
| user_id, vehicle_id, station_id | BIGINT | **NOT NULL**, FKs |
| connector_id | BIGINT | FK connectors; NULL until assigned |
| requested_energy_kwh | NUMERIC(8,2) | NOT NULL, **CHECK > 0** |
| requested_start, requested_end | TIMESTAMPTZ | NOT NULL, **CHECK end > start** |
| status | VARCHAR(24) | NOT NULL, **CHECK IN (PENDING, ASSIGNED, WAITLISTED, REJECTED, CANCELLED, COMPLETED)** |
| **status_reason** | VARCHAR(40) | CHECK IN the eleven `StatusReason` values (V4 added `INSUFFICIENT_GRID_CAPACITY`) |
| **assigned_at** | TIMESTAMPTZ | Set on assignment; live waiting time = `assigned_at − created_at` (D11) |
| **allocation_run_id** | BIGINT | FK allocation_runs |
| created_at | TIMESTAMPTZ | NOT NULL |

Table-level check: `CHECK (status <> 'ASSIGNED' OR connector_id IS NOT NULL)`.

### charging_sessions

| Column | Type | Constraints |
|---|---|---|
| id | BIGSERIAL | PK |
| booking_id | BIGINT | **NOT NULL**, UNIQUE, FK bookings |
| station_id, connector_id | BIGINT | **NOT NULL**, FKs |
| start_time, end_time | TIMESTAMPTZ | **CHECK end ≥ start when both are set** |
| energy_delivered_kwh | NUMERIC(10,3) | NOT NULL DEFAULT 0, CHECK ≥ 0 |
| allocated_power_kw | NUMERIC(8,2) | NOT NULL DEFAULT 0, CHECK ≥ 0 |
| status | VARCHAR(24) | NOT NULL, **CHECK IN (SCHEDULED, ACTIVE, COMPLETED, CANCELLED, INTERRUPTED)** |

### power_allocations

This table is append-only, so re-allocations after a grid reduction keep their history (D5).

| Column | Type | Constraints |
|---|---|---|
| id | BIGSERIAL | PK |
| session_id | BIGINT | NOT NULL, FK charging_sessions, ON DELETE CASCADE |
| requested_kw, allocated_kw | NUMERIC(8,2) | NOT NULL, ≥ 0, **CHECK allocated ≤ requested** |
| allocation_time | TIMESTAMPTZ | NOT NULL |

### simulations, simulation_results, benchmark_runs

- `simulations` follows spec §9:
  - `created_by` is a FK to users, ON DELETE SET NULL;
  - `configuration` is JSONB;
  - `status` uses `CHECK IN (PENDING, RUNNING, COMPLETED, FAILED)`.
- `simulation_results` follows spec §9:
  - `simulation_id` is NOT NULL, a FK with ON DELETE CASCADE;
  - `input_summary`, `output_summary` and `metrics` are JSONB;
  - `runtime_ns` has `CHECK ≥ 0`.
- `benchmark_runs` is a D6 addition:
  - `created_by` is ON DELETE SET NULL;
  - `random_seed` is BIGINT;
  - `configuration` is JSONB;
  - `results` is JSONB and NULL until the run completes;
  - `status` uses the same CHECK as `simulations`;
  - `created_at`.

## Overlap safeguards (V3)

Both constraints use the range `tstzrange(requested_start, requested_end, '[)')`. Because the range is
half-open, back-to-back windows such as `[10:00,11:00)` followed by `[11:00,12:00)` do not overlap.

```sql
ALTER TABLE bookings ADD CONSTRAINT bookings_no_connector_overlap
    EXCLUDE USING gist (connector_id WITH =, tstzrange(requested_start, requested_end, '[)') WITH &&)
    WHERE (status = 'ASSIGNED' AND connector_id IS NOT NULL);

ALTER TABLE bookings ADD CONSTRAINT bookings_no_vehicle_overlap
    EXCLUDE USING gist (vehicle_id WITH =, tstzrange(requested_start, requested_end, '[)') WITH &&)
    WHERE (status IN ('PENDING', 'WAITLISTED', 'ASSIGNED'));
```

- **No double-booked connector:** no two `ASSIGNED` bookings on one connector may overlap.
- **No double-booked vehicle:** a vehicle may not hold two active bookings that overlap.

Cancelled, rejected and completed bookings never block anything. The allocation service already
prevents these overlaps (D4). The constraints are the last line of defence if the service logic were
bypassed.

## Indexes

| Index | Columns | Serves |
|---|---|---|
| `idx_stations_status` | stations (status) | Operational-station filters |
| `stations_state_city_idx` | stations (state, city) | State and city filters |
| `idx_connectors_station_status` | connectors (station_id, status) | Compatibility graph, availability |
| `idx_bookings_status_start` | bookings (status, requested_start) | Eligible-booking selection per allocation interval |
| `idx_bookings_user` | bookings (user_id) | Owner-scoped booking lists |
| `idx_vehicles_user` | vehicles (user_id) | Owner-scoped vehicle lists |
| `idx_sessions_station_status` | charging_sessions (station_id, status) | Dashboard and lifecycle updates |
| `idx_power_allocations_session` | power_allocations (session_id) | Allocation history |
| `idx_simulation_results_simulation` | simulation_results (simulation_id) | Results per simulation |

The two GiST exclusion constraints also create their own indexes.

## Verified by

| Test | Count | What it checks |
|---|---|---|
| `SchemaConstraintsIT` | 8 | Both exclusion constraints, adjacent windows allowed, cancelled bookings do not block, and the CHECK constraints on SoC, empty windows, negative grid capacity and unknown status |
| `RepositoryOwnershipIT` | 5 | Owner-scoped finders, eligible-booking selection, round-trip of kW scale and JSONB |
| `DemoDataSeederIT` | 4 | The demo dataset loads idempotently; an invalid dataset is rejected before any write |

Mutation check: with `bookings_no_connector_overlap` removed,
`overlappingAssignedBookingsOnConnectorRejectedByDatabase` fails.
