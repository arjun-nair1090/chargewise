package com.chargewise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** A timed benchmark run; {@code results} stays null until the run completes (architecture D6). */
@Entity
@Table(name = "benchmark_runs")
public class BenchmarkRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "random_seed", nullable = false)
    private long randomSeed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String configuration;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String results;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RunStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BenchmarkRun() {
    }

    public BenchmarkRun(User createdBy, long randomSeed, String configuration, RunStatus status,
            Instant createdAt) {
        this.createdBy = createdBy;
        this.randomSeed = randomSeed;
        this.configuration = configuration;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void markRunning() {
        this.status = RunStatus.RUNNING;
    }

    public void complete(String results) {
        this.results = results;
        this.status = RunStatus.COMPLETED;
    }

    public void fail() {
        this.status = RunStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public String getConfiguration() {
        return configuration;
    }

    public String getResults() {
        return results;
    }

    public RunStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
