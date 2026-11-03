package com.chargewise.dto.lab;

import com.chargewise.dto.lab.SegmentTreeLabRequest.OperationType;
import java.util.List;

/** One result per range query, answered by the segment tree and by a naive scan over the same state. */
public record SegmentTreeLabResponse(int n, int operationCount, boolean agree, List<QueryResult> results,
        LabTiming algorithm, LabTiming baseline) {

    /** {@code operation} is the query's index in the request's operation list. */
    public record QueryResult(int operation, OperationType type, int from, int to, long tree, long naive) {
    }

    public record LabTiming(String name, long runtimeNs) {
    }
}
