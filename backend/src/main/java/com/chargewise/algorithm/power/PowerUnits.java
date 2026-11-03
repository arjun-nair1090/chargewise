package com.chargewise.algorithm.power;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Exact conversion between persisted kilowatts ({@code BigDecimal}) and the integer watts used
 * inside the algorithms (architecture D1). Inputs that cannot be represented exactly are
 * rejected rather than rounded, so a conversion can never push a sum over a capacity limit.
 */
public final class PowerUnits {

    private static final int WATTS_PER_KW_EXPONENT = 3;
    private static final int KW_SCALE = 2;

    private PowerUnits() {
    }

    /** {@code 7.12 kW -> 7120 W}. Rejects null, negative, sub-watt precision and out-of-range values. */
    public static long kwToWatts(BigDecimal kilowatts) {
        if (kilowatts == null) {
            throw new IllegalArgumentException("power must not be null");
        }
        if (kilowatts.signum() < 0) {
            throw new IllegalArgumentException("power must be >= 0, was " + kilowatts);
        }
        try {
            return kilowatts.movePointRight(WATTS_PER_KW_EXPONENT)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("power must be a whole number of watts within range, was "
                    + kilowatts.toPlainString() + " kW", e);
        }
    }

    /** {@code 7129 W -> 7.12 kW}, rounding down so reported power never exceeds what was allocated. */
    public static BigDecimal wattsToKw(long watts) {
        if (watts < 0) {
            throw new IllegalArgumentException("power must be >= 0, was " + watts + " W");
        }
        return BigDecimal.valueOf(watts, WATTS_PER_KW_EXPONENT).setScale(KW_SCALE, RoundingMode.DOWN);
    }
}
