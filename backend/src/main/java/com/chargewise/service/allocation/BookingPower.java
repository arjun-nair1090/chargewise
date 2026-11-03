package com.chargewise.service.allocation;

import com.chargewise.algorithm.power.PowerUnits;
import com.chargewise.entity.Booking;
import java.math.BigDecimal;
import java.time.Duration;

/** Converts a persisted booking into the integer W / Wh the algorithms use (architecture D1, D2). */
final class BookingPower {

    private BookingPower() {
    }

    /** kWh → Wh is the same exact ×1000 conversion as kW → W. */
    static long wattHours(BigDecimal kilowattHours) {
        return PowerUnits.kwToWatts(kilowattHours);
    }

    static long vehicleMaxW(Booking booking) {
        return PowerUnits.kwToWatts(booking.getVehicle().getMaxChargingKw());
    }

    static long requestedW(Booking booking) {
        long minutes = Duration.between(booking.getRequestedStart(), booking.getRequestedEnd()).toMinutes();
        return PowerProblemBuilder.requestedW(wattHours(booking.getRequestedEnergyKwh()), minutes,
                vehicleMaxW(booking));
    }
}
