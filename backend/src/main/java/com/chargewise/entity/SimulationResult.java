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

/** One algorithm's measured outcome within a simulation (spec §9). */
@Entity
@Table(name = "simulation_results")
public class SimulationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id", nullable = false)
    private Simulation simulation;

    @Column(name = "algorithm_name", nullable = false, length = 80)
    private String algorithmName;

    @Column(name = "algorithm_version", nullable = false, length = 40)
    private String algorithmVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_summary", nullable = false, columnDefinition = "jsonb")
    private String inputSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_summary", nullable = false, columnDefinition = "jsonb")
    private String outputSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metrics;

    @Column(name = "runtime_ns")
    private Long runtimeNs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SimulationResult() {
    }

    public SimulationResult(Simulation simulation, String algorithmName, String algorithmVersion,
            String inputSummary, String outputSummary, String metrics, Long runtimeNs, Instant createdAt) {
        this.simulation = simulation;
        this.algorithmName = algorithmName;
        this.algorithmVersion = algorithmVersion;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.metrics = metrics;
        this.runtimeNs = runtimeNs;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Simulation getSimulation() {
        return simulation;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public String getMetrics() {
        return metrics;
    }

    public Long getRuntimeNs() {
        return runtimeNs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
