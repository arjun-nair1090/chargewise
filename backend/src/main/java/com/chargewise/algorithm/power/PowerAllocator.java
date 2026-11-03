package com.chargewise.algorithm.power;

/** A strategy that grants each matched session a constant power for the allocation interval. */
public interface PowerAllocator {

    String name();

    PowerAllocationResult allocate(PowerAllocationProblem problem);
}
