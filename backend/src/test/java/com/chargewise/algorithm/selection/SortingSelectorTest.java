package com.chargewise.algorithm.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class SortingSelectorTest {

    private final SortingSelector selector = new SortingSelector();

    @Test
    void selectsKthSmallestWithoutMutatingInput() {
        long[] values = {9, 3, 7, 1};

        assertThat(selector.select(values, 0).value()).isEqualTo(1);
        assertThat(selector.select(values, 3).value()).isEqualTo(9);
        assertThat(values).containsExactly(9, 3, 7, 1);
    }

    @Test
    void invalidInputRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> selector.select(new long[0], 0));
        assertThatIllegalArgumentException().isThrownBy(() -> selector.select(new long[] {1}, 1));
    }
}
