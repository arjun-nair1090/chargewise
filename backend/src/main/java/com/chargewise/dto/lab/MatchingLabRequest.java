package com.chargewise.dto.lab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** A bipartite compatibility graph: request ids (left), connector ids (right) and the edges between them. */
public record MatchingLabRequest(
        @NotNull List<@NotBlank String> requests,
        @NotNull List<@NotBlank String> connectors,
        @NotNull List<@Valid @NotNull MatchingEdge> edges) {

    public record MatchingEdge(@NotBlank String request, @NotBlank String connector) {
    }
}
