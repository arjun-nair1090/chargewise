package com.chargewise.dto.lab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Initial values and an ordered list of operations: range queries {@code SUM}/{@code MAX} over the inclusive
 * index range {@code [from, to]}, and point updates {@code SET}/{@code ADD} of {@code value} at {@code index}.
 */
public record SegmentTreeLabRequest(
        @NotNull List<@NotNull Long> values,
        @NotNull List<@Valid @NotNull TreeOperation> operations) {

    public record TreeOperation(@NotNull OperationType type, Integer from, Integer to, Integer index, Long value) {
    }

    public enum OperationType {
        SUM,
        MAX,
        SET,
        ADD
    }
}
