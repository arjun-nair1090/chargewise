package com.chargewise.simulation;

import com.chargewise.entity.ConnectorType;

/**
 * Connector types by index ({@link ConnectorType#ordinal()}), with the rated powers and the population weights the
 * scenario generator draws from. Watts are integers, as everywhere in the engine.
 */
public final class ConnectorTypes {

    public static final int COUNT = ConnectorType.values().length;

    /** Rated outputs offered per type: CCS2, Type 2 AC, CHAdeMO, Bharat DC-001, Bharat AC-001. */
    private static final long[][] RATED_W = {
        {30_000, 60_000, 120_000},
        {7_400, 11_000, 22_000},
        {50_000},
        {15_000},
        {3_300},
    };

    /** How common each type is on chargers and vehicles in the default population (sums to 100). */
    private static final int[] WEIGHTS = {40, 35, 5, 10, 10};

    private ConnectorTypes() {
    }

    public static String name(int type) {
        return ConnectorType.values()[type].name();
    }

    static long[] ratedW(int type) {
        return RATED_W[type].clone();
    }

    static int weight(int type) {
        return WEIGHTS[type];
    }
}
