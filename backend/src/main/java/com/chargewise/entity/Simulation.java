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

/** A seeded, reproducible scenario run (architecture D9/D10); configuration is stored as JSON. */
@Entity
@Table(name = "simulations")
public class Simulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "random_seed", nullable = false)
    private long randomSeed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String configuration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RunStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Simulation() {
    }

    public Simulation(User createdBy, String name, long randomSeed, String configuration, RunStatus status,
            Instant createdAt) {
        this.createdBy = createdBy;
        this.name = name;
        this.randomSeed = randomSeed;
        this.configuration = configuration;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getName() {
        return name;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public String getConfiguration() {
        return configuration;
    }

    public RunStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
