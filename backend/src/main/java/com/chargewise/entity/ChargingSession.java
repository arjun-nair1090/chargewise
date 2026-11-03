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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** The (simulated) charging of one assigned booking at constant allocated power (architecture D2/D3). */
@Entity
@Table(name = "charging_sessions")
public class ChargingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connector_id", nullable = false)
    private Connector connector;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "energy_delivered_kwh", nullable = false, precision = 10, scale = 3)
    private BigDecimal energyDeliveredKwh = BigDecimal.ZERO.setScale(3);

    @Column(name = "allocated_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal allocatedPowerKw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SessionStatus status;

    protected ChargingSession() {
    }

    /** A newly scheduled session; it becomes ACTIVE when its window starts. */
    public ChargingSession(Booking booking, Station station, Connector connector, BigDecimal allocatedPowerKw) {
        this.booking = booking;
        this.station = station;
        this.connector = connector;
        this.allocatedPowerKw = allocatedPowerKw;
        this.status = SessionStatus.SCHEDULED;
    }

    /**
     * A booking has at most one session (spec §9), so a waitlisted booking that is matched again reuses its
     * cancelled session, keeping that session's power-allocation history.
     */
    public void reschedule(Connector connector, BigDecimal allocatedPowerKw) {
        this.connector = connector;
        this.allocatedPowerKw = allocatedPowerKw;
        this.startTime = null;
        this.endTime = null;
        this.energyDeliveredKwh = BigDecimal.ZERO.setScale(3);
        this.status = SessionStatus.SCHEDULED;
    }

    public void setAllocatedPowerKw(BigDecimal allocatedPowerKw) {
        this.allocatedPowerKw = allocatedPowerKw;
    }

    public void start(Instant at) {
        this.startTime = at;
        this.status = SessionStatus.ACTIVE;
    }

    public void recordEnergy(BigDecimal energyDeliveredKwh) {
        this.energyDeliveredKwh = energyDeliveredKwh;
    }

    public void finish(SessionStatus finalStatus, Instant at) {
        this.endTime = at;
        this.status = finalStatus;
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public Station getStation() {
        return station;
    }

    public Connector getConnector() {
        return connector;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public BigDecimal getEnergyDeliveredKwh() {
        return energyDeliveredKwh;
    }

    public BigDecimal getAllocatedPowerKw() {
        return allocatedPowerKw;
    }

    public SessionStatus getStatus() {
        return status;
    }
}
