package com.chargewise.algorithm.power;

import java.util.SplittableRandom;

/** Seeded random power problems shared by the allocator tests. */
final class PowerProblemFixtures {

    private PowerProblemFixtures() {
    }

    static PowerAllocationProblem randomDirect(SplittableRandom random) {
        int stations = 1 + random.nextInt(6);
        int sessions = random.nextInt(13);
        long[] stationCapacity = new long[stations];
        for (int i = 0; i < stations; i++) {
            stationCapacity[i] = random.nextInt(0, 400_001);
        }
        int[] sessionStation = new int[sessions];
        long[] connectorMax = new long[sessions];
        long[] requested = new long[sessions];
        long[] vehicleMax = new long[sessions];
        for (int j = 0; j < sessions; j++) {
            sessionStation[j] = random.nextInt(stations);
            connectorMax[j] = random.nextInt(1, 150_001);
            requested[j] = random.nextInt(0, 150_001);
            vehicleMax[j] = random.nextInt(1, 150_001);
        }
        return PowerAllocationProblem.direct(stationCapacity, sessionStation, connectorMax, requested, vehicleMax);
    }

    static PowerAllocationProblem randomWithFeeders(SplittableRandom random) {
        PowerAllocationProblem base = randomDirect(random);
        int stations = base.stationCount();
        int feeders = 1 + random.nextInt(4);
        long[] feederCapacity = new long[feeders];
        for (int f = 0; f < feeders; f++) {
            feederCapacity[f] = random.nextInt(0, 500_001);
        }
        int[] linkFeeder = new int[stations * 2];
        int[] linkStation = new int[stations * 2];
        int links = 0;
        for (int i = 0; i < stations; i++) {
            int first = random.nextInt(feeders);
            linkFeeder[links] = first;
            linkStation[links++] = i;
            int second = random.nextInt(feeders);
            if (second != first && random.nextBoolean()) {
                linkFeeder[links] = second;
                linkStation[links++] = i;
            }
        }
        FeederTopology topology = new FeederTopology(
                feederCapacity,
                java.util.Arrays.copyOf(linkFeeder, links),
                java.util.Arrays.copyOf(linkStation, links));
        return PowerAllocationProblem.withFeeders(base.stationCapacityW(), base.sessionStation(),
                base.sessionConnectorMaxW(), base.sessionRequestedW(), base.sessionVehicleMaxW(), topology);
    }
}
