package com.chargewise.algorithm.power;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PowerUnitsTest {

    @Test
    void kilowattsConvertExactlyToWatts() {
        assertThat(PowerUnits.kwToWatts(new BigDecimal("7.12"))).isEqualTo(7_120L);
        assertThat(PowerUnits.kwToWatts(new BigDecimal("350"))).isEqualTo(350_000L);
        assertThat(PowerUnits.kwToWatts(new BigDecimal("0.001"))).isEqualTo(1L);
        assertThat(PowerUnits.kwToWatts(BigDecimal.ZERO)).isZero();
    }

    @Test
    void wattsConvertToKilowattsRoundingDown() {
        assertThat(PowerUnits.wattsToKw(7_129L)).isEqualByComparingTo("7.12");
        assertThat(PowerUnits.wattsToKw(7_129L).scale()).isEqualTo(2);
        assertThat(PowerUnits.wattsToKw(350_000L)).isEqualByComparingTo("350.00");
    }

    @Test
    void subWattPrecisionRejectedRatherThanRounded() {
        assertThatIllegalArgumentException().isThrownBy(() -> PowerUnits.kwToWatts(new BigDecimal("7.1234")));
    }

    @Test
    void negativeOrMissingValuesRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> PowerUnits.kwToWatts(new BigDecimal("-1")));
        assertThatIllegalArgumentException().isThrownBy(() -> PowerUnits.kwToWatts(null));
        assertThatIllegalArgumentException().isThrownBy(() -> PowerUnits.wattsToKw(-1L));
    }

    @Test
    void valuesBeyondLongRangeRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PowerUnits.kwToWatts(new BigDecimal("1e30")));
    }
}
