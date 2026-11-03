package com.chargewise.repository;

import com.chargewise.entity.Booking;
import com.chargewise.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    Page<Booking> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Booking> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Booking> findAllByStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);

    @Query("select b.status, count(b) from Booking b group by b.status")
    List<Object[]> countByStatus();

    /** Live waiting time {@code assigned_at − created_at} in whole seconds (architecture D11). */
    @Query(value = """
            select cast(floor(extract(epoch from (assigned_at - created_at))) as bigint)
            from bookings where assigned_at is not null""", nativeQuery = true)
    List<Long> assignWaitSeconds();

    /** Submission times of requests still waiting for an assignment. */
    @Query("""
            select b.createdAt from Booking b
            where b.status in (com.chargewise.entity.BookingStatus.PENDING,
                               com.chargewise.entity.BookingStatus.WAITLISTED)""")
    List<Instant> queuedSince();

    List<Booking> findAllByStationIdAndStatus(Long stationId, BookingStatus status);

    List<Booking> findAllByConnectorIdAndStatus(Long connectorId, BookingStatus status);

    /**
     * PENDING and WAITLISTED bookings whose window overlaps {@code [from, to)}, oldest request first, locked
     * for the allocation transaction (architecture D4 step 2).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from Booking b
            where b.status in (com.chargewise.entity.BookingStatus.PENDING,
                               com.chargewise.entity.BookingStatus.WAITLISTED)
              and b.requestedStart < :to and b.requestedEnd > :from
            order by b.createdAt asc, b.id asc""")
    List<Booking> findEligibleForAllocation(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * PENDING and WAITLISTED bookings whose window started at or before {@code now}, whatever interval a run covers,
     * so a run can close requests no earlier run reached before they began. Locked like the eligible set.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from Booking b
            where b.status in (com.chargewise.entity.BookingStatus.PENDING,
                               com.chargewise.entity.BookingStatus.WAITLISTED)
              and b.requestedStart <= :now
            order by b.createdAt asc, b.id asc""")
    List<Booking> findQueuedStartedBy(@Param("now") Instant now);

    /** Service-level twin of the {@code bookings_no_vehicle_overlap} constraint, for a clear 409 message. */
    @Query("""
            select count(b) > 0 from Booking b
            where b.vehicle.id = :vehicleId
              and b.status in (com.chargewise.entity.BookingStatus.PENDING,
                               com.chargewise.entity.BookingStatus.WAITLISTED,
                               com.chargewise.entity.BookingStatus.ASSIGNED)
              and b.requestedStart < :end and b.requestedEnd > :start""")
    boolean existsActiveOverlapForVehicle(@Param("vehicleId") Long vehicleId, @Param("start") Instant start,
            @Param("end") Instant end);

    /** ASSIGNED bookings at the given stations whose window overlaps {@code [from, to)}. */
    @Query("""
            select b from Booking b
            where b.station.id in :stationIds
              and b.status = com.chargewise.entity.BookingStatus.ASSIGNED
              and b.requestedStart < :to and b.requestedEnd > :from""")
    List<Booking> findAssignedOverlapping(@Param("stationIds") Collection<Long> stationIds,
            @Param("from") Instant from, @Param("to") Instant to);
}
