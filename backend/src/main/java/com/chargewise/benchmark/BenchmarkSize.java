package com.chargewise.benchmark;

/**
 * Dataset sizes (project brief §10). Matching uses {@code requests x connectors}; power allocation has one session per
 * connector; selection and range queries use {@code valueCount} items.
 */
public enum BenchmarkSize {
    SMALL(100, 30, 1_000),
    MEDIUM(1_000, 300, 10_000),
    LARGE(10_000, 3_000, 100_000);

    private final int requests;
    private final int connectors;
    private final int valueCount;

    BenchmarkSize(int requests, int connectors, int valueCount) {
        this.requests = requests;
        this.connectors = connectors;
        this.valueCount = valueCount;
    }

    public int requests() {
        return requests;
    }

    public int connectors() {
        return connectors;
    }

    public int valueCount() {
        return valueCount;
    }

    public String shortName() {
        return name().substring(0, 1);
    }
}
