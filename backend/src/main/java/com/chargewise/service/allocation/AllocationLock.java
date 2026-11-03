package com.chargewise.service.allocation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The transaction-scoped PostgreSQL advisory lock that serialises allocation runs (D4 step 1). Anything that changes
 * the rows a run plans with (station grid limits and status, connector status, booking cancellation) takes the same
 * lock first, so it either completes before a run reads those rows or waits until the run commits.
 */
@Component
public class AllocationLock {

    /** The advisory lock key. */
    public static final long ID = 0x43570001L;

    private final JdbcTemplate jdbc;

    public AllocationLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Blocks until the lock is free, then holds it until the surrounding transaction ends. */
    public void acquire() {
        jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?)", Integer.class, ID);
    }
}
