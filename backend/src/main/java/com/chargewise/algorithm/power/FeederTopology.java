package com.chargewise.algorithm.power;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared distribution feeders (architecture D12). Link {@code i} connects feeder
 * {@code linkFeeder[i]} to station {@code linkStation[i]}; a station may have several links
 * (dual-fed), a feeder may serve several stations. Station indices are range-checked by
 * {@link PowerAllocationProblem#withFeeders}, which knows the station count.
 */
public record FeederTopology(long[] feederCapacityW, int[] linkFeeder, int[] linkStation) {

    public FeederTopology {
        if (feederCapacityW == null || linkFeeder == null || linkStation == null) {
            throw new IllegalArgumentException("feeder arrays must not be null");
        }
        if (linkFeeder.length != linkStation.length) {
            throw new IllegalArgumentException("linkFeeder and linkStation must have equal length");
        }
        for (int f = 0; f < feederCapacityW.length; f++) {
            if (feederCapacityW[f] < 0) {
                throw new IllegalArgumentException("feeder " + f + " capacity must be >= 0");
            }
        }
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < linkFeeder.length; i++) {
            if (linkFeeder[i] < 0 || linkFeeder[i] >= feederCapacityW.length) {
                throw new IllegalArgumentException("link " + i + " references unknown feeder " + linkFeeder[i]);
            }
            if (linkStation[i] < 0) {
                throw new IllegalArgumentException("link " + i + " references negative station " + linkStation[i]);
            }
            if (!seen.add(((long) linkFeeder[i] << 32) | linkStation[i])) {
                throw new IllegalArgumentException("duplicate link feeder " + linkFeeder[i]
                        + " -> station " + linkStation[i]);
            }
        }
        feederCapacityW = feederCapacityW.clone();
        linkFeeder = linkFeeder.clone();
        linkStation = linkStation.clone();
    }

    public int feederCount() {
        return feederCapacityW.length;
    }

    public int linkCount() {
        return linkFeeder.length;
    }

    @Override
    public long[] feederCapacityW() {
        return feederCapacityW.clone();
    }

    @Override
    public int[] linkFeeder() {
        return linkFeeder.clone();
    }

    @Override
    public int[] linkStation() {
        return linkStation.clone();
    }
}
