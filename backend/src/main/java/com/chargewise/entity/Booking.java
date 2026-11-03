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
import java.time.Instant;

/**
 * A driver's charging request for a half-open window {@code [requestedStart, requestedEnd)} at one station.
 * Status transitions follow architecture D3; the connector is set only when the booking is assigned.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connector_id")
    private Connector connector;

    @Column(name = "requested_energy_kwh", nullable = false, precision = 8, scale = 2)
    private BigDecimal requestedEnergyKwh;

    @Column(name = "requested_start", nullable = false)
    private Instant requestedStart;

    @Column(name = "requested_end", nullable = false)
    private Instant requestedEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_reason", length = 40)
    private StatusReason statusReason;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_run_id")
    private AllocationRun allocationRun;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Booking() {
    }

    /** A new request; every booking starts {@link BookingStatus#PENDING}. */
    public Booking(User user, Vehicle vehicle, Station station, BigDecimal requestedEnergyKwh,
            Instant requestedStart, Instant requestedEnd, Instant createdAt) {
        this.user = user;
        this.vehicle = vehicle;
        this.station = station;
        this.requestedEnergyKwh = requestedEnergyKwh;
        this.requestedStart = requestedStart;
        this.requestedEnd = requestedEnd;
        this.status = BookingStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void assign(Connector connector, AllocationRun run, Instant at) {
        this.connector = connector;
        this.allocationRun = run;
        this.assignedAt = at;
        this.status = BookingStatus.ASSIGNED;
        this.statusReason = null;
    }

    /** Temporary impossibility; the connector is released and the booking stays eligible (D3, D5). */
    public void waitlist(StatusReason reason) {
        this.connector = null;
        this.assignedAt = null;
        this.status = BookingStatus.WAITLISTED;
        this.statusReason = reason;
    }

    /** Structural impossibility (D3). */
    public void reject(StatusReason reason) {
        this.connector = null;
        this.assignedAt = null;
        this.status = BookingStatus.REJECTED;
        this.statusReason = reason;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public void complete() {
        this.status = BookingStatus.COMPLETED;
    }

    /** An outage cut the window short after it started: the booking ends, keeping its connector as history (D5). */
    public void endByOutage(StatusReason reason) {
        this.status = BookingStatus.COMPLETED;
        this.statusReason = reason;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Station getStation() {
        return station;
    }

    public Connector getConnector() {
        return connector;
    }

    public BigDecimal getRequestedEnergyKwh() {
        return requestedEnergyKwh;
    }

    public Instant getRequestedStart() {
        return requestedStart;
    }

    public Instant getRequestedEnd() {
        return requestedEnd;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public StatusReason getStatusReason() {
        return statusReason;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public AllocationRun getAllocationRun() {
        return allocationRun;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
