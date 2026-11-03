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
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One execution of the allocation pipeline; {@code result} holds the full result JSON (architecture D4/D6). */
@Entity
@Table(name = "allocation_runs")
public class AllocationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interval_start", nullable = false)
    private Instant intervalStart;

    @Column(name = "interval_end", nullable = false)
    private Instant intervalEnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AllocationRun() {
    }

    public AllocationRun(Instant intervalStart, Instant intervalEnd, User createdBy, String result,
            Instant createdAt) {
        this.intervalStart = intervalStart;
        this.intervalEnd = intervalEnd;
        this.createdBy = createdBy;
        this.result = result;
        this.createdAt = createdAt;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Long getId() {
        return id;
    }

    public Instant getIntervalStart() {
        return intervalStart;
    }

    public Instant getIntervalEnd() {
        return intervalEnd;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
