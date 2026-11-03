package com.chargewise.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The assessed algorithms, and the simulation engine and benchmark harness built on them, must stay framework-agnostic: callable from plain
 * unit tests and free of Spring, Jakarta (JPA/validation) and Jackson types (architecture §2).
 */
class AlgorithmPurityTest {

    private static final List<Path> PURE_SOURCES =
            List.of(Path.of("src/main/java/com/chargewise/algorithm"), Path.of("src/main/java/com/chargewise/simulation"),
                    Path.of("src/main/java/com/chargewise/benchmark"));
    private static final List<String> FORBIDDEN_PREFIXES =
            List.of("org.springframework", "jakarta.", "javax.persistence", "com.fasterxml", "tools.jackson");

    @Test
    void algorithmPackagesImportNoFrameworkTypes() throws IOException {
        for (Path root : PURE_SOURCES) {
            assertPure(root);
        }
    }

    private static void assertPure(Path root) throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(root)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(sources).as(root + " sources found").isNotEmpty();

        for (Path source : sources) {
            List<String> offending = Files.readAllLines(source).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("import "))
                    .filter(line -> FORBIDDEN_PREFIXES.stream()
                            .anyMatch(prefix -> line.startsWith("import " + prefix) || line.startsWith("import static " + prefix)))
                    .toList();
            assertThat(offending).as(source.toString()).isEmpty();
        }
    }
}
