package com.chargewise.repository;

import com.chargewise.entity.ChargingSession;
import com.chargewise.entity.SessionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {

    Optional<ChargingSession> findByIdAndBookingUserId(Long id, Long userId);

    Optional<ChargingSession> findByBookingId(Long bookingId);

    List<ChargingSession> findAllByBookingIdIn(Collection<Long> bookingIds);

    Page<ChargingSession> findAllByBookingUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<ChargingSession> findAllByOrderByIdDesc(Pageable pageable);

    Page<ChargingSession> findAllByStatusOrderByIdDesc(SessionStatus status, Pageable pageable);

    @Query("select s.status, count(s) from ChargingSession s group by s.status")
    List<Object[]> countByStatus();

    @Query("""
            select coalesce(sum(s.allocatedPowerKw), 0) from ChargingSession s
            where s.status = com.chargewise.entity.SessionStatus.ACTIVE""")
    BigDecimal activePowerKw();

    /** SCHEDULED and ACTIVE sessions whose booking window has started by {@code now}. */
    @Query("""
            select s from ChargingSession s join fetch s.booking b
            where s.status in (com.chargewise.entity.SessionStatus.SCHEDULED,
                               com.chargewise.entity.SessionStatus.ACTIVE)
              and b.requestedStart <= :now""")
    List<ChargingSession> findLiveStartedBy(@Param("now") Instant now);

    List<ChargingSession> findAllByStationIdAndStatusInOrderByIdAsc(Long stationId, Collection<SessionStatus> statuses);

    /** SCHEDULED and ACTIVE sessions at the stations whose booking window overlaps {@code [from, to)}. */
    @Query("""
            select s from ChargingSession s join fetch s.booking b
            where s.station.id in :stationIds
              and s.status in (com.chargewise.entity.SessionStatus.SCHEDULED,
                               com.chargewise.entity.SessionStatus.ACTIVE)
              and b.requestedStart < :to and b.requestedEnd > :from""")
    List<ChargingSession> findCommitted(@Param("stationIds") Collection<Long> stationIds, @Param("from") Instant from,
            @Param("to") Instant to);
}
