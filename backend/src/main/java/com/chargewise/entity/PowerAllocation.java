package com.chargewise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Append-only record of each power decision for a session, so re-allocations keep their history (D5). */
@Entity
@Table(name = "power_allocations")
public class PowerAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChargingSession session;

    @Column(name = "requested_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal requestedKw;

    @Column(name = "allocated_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal allocatedKw;

    @Column(name = "allocation_time", nullable = false)
    private Instant allocationTime;

    protected PowerAllocation() {
    }

    public PowerAllocation(ChargingSession session, BigDecimal requestedKw, BigDecimal allocatedKw,
            Instant allocationTime) {
        this.session = session;
        this.requestedKw = requestedKw;
        this.allocatedKw = allocatedKw;
        this.allocationTime = allocationTime;
    }

    public Long getId() {
        return id;
    }

    public ChargingSession getSession() {
        return session;
    }

    public BigDecimal getRequestedKw() {
        return requestedKw;
    }

    public BigDecimal getAllocatedKw() {
        return allocatedKw;
    }

    public Instant getAllocationTime() {
        return allocationTime;
    }
}
