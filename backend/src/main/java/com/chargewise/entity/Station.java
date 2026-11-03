package com.chargewise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stations")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "grid_capacity_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal gridCapacityKw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Station() {
    }

    public Station(String name, String address, BigDecimal latitude, BigDecimal longitude,
            BigDecimal gridCapacityKw, StationStatus status, Instant createdAt) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.gridCapacityKw = gridCapacityKw;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void update(String name, String address, BigDecimal latitude, BigDecimal longitude,
            BigDecimal gridCapacityKw, StationStatus status) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.gridCapacityKw = gridCapacityKw;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getGridCapacityKw() {
        return gridCapacityKw;
    }

    public StationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
