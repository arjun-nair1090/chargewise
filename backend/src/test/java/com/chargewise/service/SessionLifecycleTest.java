package com.chargewise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chargewise.entity.Booking;
import com.chargewise.entity.BookingStatus;
import com.chargewise.entity.ChargingSession;
import com.chargewise.entity.SessionStatus;
import com.chargewise.repository.ChargingSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionLifecycleTest {

    private static final Instant START = Instant.parse("2026-11-05T10:00:00Z");
    private static final Instant END = START.plus(Duration.ofHours(1));

    private static ChargingSession session(String requestedKwh, String allocatedKw) {
        Booking booking = new Booking(null, null, null, new BigDecimal(requestedKwh), START, END,
                START.minus(Duration.ofDays(1)));
        return new ChargingSession(booking, null, null, new BigDecimal(allocatedKw));
    }

    private static Instant at(Clock clock) {
        return clock.instant();
    }

    @Test
    void sessionProgressFollowsFixedClock() {
        ChargingSession slow = session("20.00", "7.50");  // delivers 7.5 kWh over the hour
        ChargingSession fast = session("20.00", "50.00"); // reaches the 20 kWh request after 24 min
        ChargingSessionRepository repository = mock(ChargingSessionRepository.class);
        when(repository.findLiveStartedBy(any())).thenReturn(List.of(slow, fast));
        SessionLifecycleService lifecycle = new SessionLifecycleService(repository);

        lifecycle.advance(at(Clock.fixed(START.plus(Duration.ofMinutes(30)), ZoneOffset.UTC)));

        assertThat(slow.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(slow.getStartTime()).isEqualTo(START);
        assertThat(slow.getEnergyDeliveredKwh()).isEqualByComparingTo("3.750");
        assertThat(slow.getEnergyDeliveredKwh().scale()).isEqualTo(3);
        assertThat(fast.getEnergyDeliveredKwh()).isEqualByComparingTo("20.000");

        lifecycle.advance(at(Clock.fixed(START.plus(Duration.ofMinutes(40)), ZoneOffset.UTC)));
        assertThat(slow.getEnergyDeliveredKwh()).isEqualByComparingTo("5.000");
        assertThat(slow.getStatus()).isEqualTo(SessionStatus.ACTIVE);

        // The tick after the window ends completes the session at the window end, not at the tick.
        lifecycle.advance(at(Clock.fixed(END.plus(Duration.ofMinutes(5)), ZoneOffset.UTC)));
        assertThat(slow.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(slow.getEndTime()).isEqualTo(END);
        assertThat(slow.getEnergyDeliveredKwh()).isEqualByComparingTo("7.500");
        assertThat(slow.getBooking().getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(fast.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(fast.getEnergyDeliveredKwh()).isEqualByComparingTo("20.000");
    }

    @Test
    void energyIsRoundedDownToWattHours() {
        ChargingSession session = session("50.00", "7.00");
        ChargingSessionRepository repository = mock(ChargingSessionRepository.class);
        when(repository.findLiveStartedBy(any())).thenReturn(List.of(session));

        // 7 kW for 100 s = 0.19444… kWh, reported as 0.194 (never more than was delivered).
        new SessionLifecycleService(repository).advance(START.plusSeconds(100));

        assertThat(session.getEnergyDeliveredKwh()).isEqualByComparingTo("0.194");
    }
}
