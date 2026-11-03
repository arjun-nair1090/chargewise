package com.chargewise.service;

import com.chargewise.entity.Booking;
import com.chargewise.entity.ChargingSession;
import com.chargewise.entity.SessionStatus;
import com.chargewise.repository.ChargingSessionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulated charging progress (architecture D3): a session is ACTIVE during its booking window and COMPLETED
 * once the window ends. Times come from the window, not from when the tick ran, so progress is the same
 * however late the scheduler fires. Energy follows the constant-power model, capped at the request.
 */
@Service
public class SessionLifecycleService {

    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    private final ChargingSessionRepository sessions;

    public SessionLifecycleService(ChargingSessionRepository sessions) {
        this.sessions = sessions;
    }

    /** Advances every session whose window has started by {@code now}; returns how many were updated. */
    @Transactional
    public int advance(Instant now) {
        List<ChargingSession> started = sessions.findLiveStartedBy(now);
        started.forEach(session -> progress(session, now));
        return started.size();
    }

    private static void progress(ChargingSession session, Instant now) {
        Booking booking = session.getBooking();
        Instant start = booking.getRequestedStart();
        Instant end = booking.getRequestedEnd();
        if (session.getStatus() == SessionStatus.SCHEDULED) {
            session.start(start);
        }
        boolean finished = !now.isBefore(end);
        long elapsedSeconds = Duration.between(start, finished ? end : now).toSeconds();
        BigDecimal delivered = session.getAllocatedPowerKw().multiply(BigDecimal.valueOf(elapsedSeconds))
                .divide(SECONDS_PER_HOUR, 3, RoundingMode.DOWN);
        session.recordEnergy(delivered.min(booking.getRequestedEnergyKwh().setScale(3)));
        if (finished) {
            session.finish(SessionStatus.COMPLETED, end);
            booking.complete();
        }
    }
}
