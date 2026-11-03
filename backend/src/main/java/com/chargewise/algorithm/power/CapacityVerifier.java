package com.chargewise.algorithm.power;

import java.util.ArrayList;
import java.util.List;

/**
 * Independent check that an allocation respects every physical limit, written without reference
 * to how any allocator works. The allocation pipeline aborts on any violation, and every test,
 * simulation and benchmark reports the violation count (target: zero).
 */
public final class CapacityVerifier {

    private CapacityVerifier() {
    }

    /** Returns human-readable violations; an empty list means the allocation is feasible. */
    public static List<String> violations(PowerAllocationProblem problem, PowerAllocationResult result) {
        List<String> problems = new ArrayList<>();
        long[] sessions = result.sessionAllocatedW();
        long[] stationCapacity = problem.stationCapacityW();
        int[] sessionStation = problem.sessionStation();
        long[] connectorMax = problem.sessionConnectorMaxW();

        if (sessions.length != problem.sessionCount()
                || result.stationAllocatedW().length != problem.stationCount()) {
            problems.add("result dimensions do not match the problem");
            return problems;
        }

        long[] stationSum = new long[problem.stationCount()];
        long total = 0;
        for (int j = 0; j < sessions.length; j++) {
            if (sessions[j] < 0) {
                problems.add("session " + j + " has negative allocation " + sessions[j]);
            }
            if (sessions[j] > connectorMax[j]) {
                problems.add("session " + j + " allocation " + sessions[j] + " exceeds connector limit " + connectorMax[j]);
            }
            if (sessions[j] > problem.sessionDemandW(j)) {
                problems.add("session " + j + " allocation " + sessions[j]
                        + " exceeds demand (min of requested and vehicle max) " + problem.sessionDemandW(j));
            }
            stationSum[sessionStation[j]] += sessions[j];
            total += sessions[j];
        }

        long[] reportedStation = result.stationAllocatedW();
        for (int i = 0; i < stationSum.length; i++) {
            if (stationSum[i] > stationCapacity[i]) {
                problems.add("station " + i + " draws " + stationSum[i] + " beyond grid limit " + stationCapacity[i]);
            }
            if (reportedStation[i] != stationSum[i]) {
                problems.add("station " + i + " reports " + reportedStation[i] + " but its sessions sum to " + stationSum[i]);
            }
        }

        if (problem.hasFeeders()) {
            checkFeeders(problem, result.linkFlowW(), stationSum, problems);
        } else if (result.linkFlowW().length != 0) {
            problems.add("link flows reported for a problem without feeders");
        }

        if (result.totalAllocatedW() != total) {
            problems.add("total allocated " + result.totalAllocatedW() + " but sessions sum to " + total);
        }
        if (result.totalRequestedW() - result.totalAllocatedW() != result.unservedW()) {
            problems.add("unserved " + result.unservedW() + " is not requested minus allocated total");
        }
        return problems;
    }

    private static void checkFeeders(PowerAllocationProblem problem, long[] linkFlow, long[] stationSum,
            List<String> problems) {
        FeederTopology feeders = problem.feeders();
        if (linkFlow.length != feeders.linkCount()) {
            problems.add("expected " + feeders.linkCount() + " link flows, got " + linkFlow.length);
            return;
        }
        int[] linkFeeder = feeders.linkFeeder();
        int[] linkStation = feeders.linkStation();
        long[] feederCapacity = feeders.feederCapacityW();
        long[] feederSum = new long[feeders.feederCount()];
        long[] intoStation = new long[problem.stationCount()];
        for (int l = 0; l < linkFlow.length; l++) {
            if (linkFlow[l] < 0) {
                problems.add("link " + l + " has negative flow " + linkFlow[l]);
            }
            feederSum[linkFeeder[l]] += linkFlow[l];
            intoStation[linkStation[l]] += linkFlow[l];
        }
        for (int f = 0; f < feederSum.length; f++) {
            if (feederSum[f] > feederCapacity[f]) {
                problems.add("feeder " + f + " supplies " + feederSum[f] + " beyond rating " + feederCapacity[f]);
            }
        }
        for (int i = 0; i < intoStation.length; i++) {
            if (intoStation[i] != stationSum[i]) {
                problems.add("links into station " + i + " total " + intoStation[i]
                        + " but the station allocated " + stationSum[i]);
            }
        }
    }
}
