package com.chargewise.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "registration_number", nullable = false, unique = true, length = 40)
    private String registrationNumber;

    @Column(name = "battery_capacity_kwh", nullable = false, precision = 8, scale = 2)
    private BigDecimal batteryCapacityKwh;

    @Column(name = "current_soc", nullable = false, precision = 5, scale = 2)
    private BigDecimal currentSoc;

    @Column(name = "max_charging_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxChargingKw;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vehicle_connector_types", joinColumns = @JoinColumn(name = "vehicle_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 40)
    private Set<ConnectorType> connectorTypes = EnumSet.noneOf(ConnectorType.class);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Vehicle() {
    }

    public Vehicle(User user, String registrationNumber, BigDecimal batteryCapacityKwh, BigDecimal currentSoc,
            BigDecimal maxChargingKw, Set<ConnectorType> connectorTypes, Instant createdAt) {
        this.user = user;
        this.registrationNumber = registrationNumber;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.currentSoc = currentSoc;
        this.maxChargingKw = maxChargingKw;
        this.connectorTypes = EnumSet.copyOf(connectorTypes);
        this.createdAt = createdAt;
    }

    public void update(String registrationNumber, BigDecimal batteryCapacityKwh, BigDecimal currentSoc,
            BigDecimal maxChargingKw, Set<ConnectorType> connectorTypes) {
        this.registrationNumber = registrationNumber;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.currentSoc = currentSoc;
        this.maxChargingKw = maxChargingKw;
        this.connectorTypes.clear();
        this.connectorTypes.addAll(connectorTypes);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public BigDecimal getBatteryCapacityKwh() {
        return batteryCapacityKwh;
    }

    public BigDecimal getCurrentSoc() {
        return currentSoc;
    }

    public BigDecimal getMaxChargingKw() {
        return maxChargingKw;
    }

    /** Energy the battery can still take, {@code capacity × (100 − SoC) / 100}, computed exactly. */
    public BigDecimal batteryHeadroomKwh() {
        return batteryCapacityKwh.multiply(BigDecimal.valueOf(100).subtract(currentSoc)).movePointLeft(2);
    }

    public Set<ConnectorType> getConnectorTypes() {
        return Set.copyOf(connectorTypes);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
