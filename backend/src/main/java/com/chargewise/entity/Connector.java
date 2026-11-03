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
import java.math.BigDecimal;

@Entity
@Table(name = "connectors")
public class Connector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 40)
    private ConnectorType connectorType;

    @Column(name = "max_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxPowerKw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConnectorStatus status;

    protected Connector() {
    }

    public Connector(Station station, ConnectorType connectorType, BigDecimal maxPowerKw, ConnectorStatus status) {
        this.station = station;
        this.connectorType = connectorType;
        this.maxPowerKw = maxPowerKw;
        this.status = status;
    }

    public void update(ConnectorType connectorType, BigDecimal maxPowerKw, ConnectorStatus status) {
        this.connectorType = connectorType;
        this.maxPowerKw = maxPowerKw;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Station getStation() {
        return station;
    }

    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public BigDecimal getMaxPowerKw() {
        return maxPowerKw;
    }

    public ConnectorStatus getStatus() {
        return status;
    }
}
