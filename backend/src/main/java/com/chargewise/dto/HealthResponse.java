package com.chargewise.dto;

/** Liveness of the API process and reachability of its database. */
public record HealthResponse(String status, String database, String version) {
}
