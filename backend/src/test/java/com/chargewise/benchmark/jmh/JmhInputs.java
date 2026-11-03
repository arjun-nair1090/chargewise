package com.chargewise.benchmark.jmh;

/**
 * Shared settings for the JMH microbenchmarks, the controlled counterpart of the in-application
 * {@code BenchmarkRunner}. They time the same seeded inputs ({@code BenchmarkInputs}) with JMH's forked JVM,
 * warm-up and dead-code protection.
 *
 * <p>Run from {@code backend/} (the classpath separator is {@code ;} on Windows and {@code :} elsewhere):
 *
 * <pre>
 * ./mvnw -B test-compile dependency:build-classpath -Dmdep.outputFile=target/jmh.classpath -Dmdep.includeScope=test
 * java -cp "target/test-classes;target/classes;$(cat target/jmh.classpath)" org.openjdk.jmh.Main \
 *     -rf json -rff target/jmh-result.json
 * </pre>
 *
 * <p>Append a regular expression to run one class (for example {@code MatchingBenchmark}) and {@code -p size=LARGE}
 * to override a parameter; the defaults stop at MEDIUM so a full run stays within minutes.
 */
final class JmhInputs {

    /** Every JMH input derives from this seed through {@code BenchmarkRunner.caseSeed}. */
    static final long SEED = 20261103L;

    private JmhInputs() {
    }
}
