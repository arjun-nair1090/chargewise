package com.chargewise.service.demo;

import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import com.chargewise.entity.StationStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The demo dataset produced by {@code scripts/generate_dataset.py}: real Open Charge Map and OpenStreetMap stations
 * across India and real EV models, with made-up owners, grid limits and requests. Each record's {@code source} says
 * where it came from. Records use external string keys ({@code S-0001}, {@code C-00001}, registration numbers) that
 * the seeder resolves to database ids.
 */
public record DemoDataset(
        List<StationRecord> stations,
        List<ConnectorRecord> connectors,
        List<VehicleRecord> vehicles,
        List<RequestRecord> requests) {

    public record StationRecord(String id, String name, String address, String city, String state,
            BigDecimal latitude, BigDecimal longitude, BigDecimal gridCapacityKw, StationStatus status, String source) {
    }

    public record ConnectorRecord(String id, String stationId, ConnectorType connectorType, BigDecimal maxPowerKw,
            ConnectorStatus status, String source) {
    }

    public record VehicleRecord(String registrationNumber, String ownerEmail, String ownerName, String homeCity,
            String homeState, String model, BigDecimal batteryCapacityKwh, BigDecimal currentSoc, BigDecimal maxChargingKw,
            List<ConnectorType> connectorTypes, String source) {
    }

    /** Window = {@code [base + startOffsetMinutes, + durationMinutes)} where base is the next slot boundary. */
    public record RequestRecord(String id, String registrationNumber, String stationId,
            BigDecimal requestedEnergyKwh, int startOffsetMinutes, int durationMinutes, String source) {
    }

    public static DemoDataset load(Path directory, JsonMapper mapper) {
        return new DemoDataset(
                read(directory, "stations.json", mapper, new TypeReference<>() { }),
                read(directory, "connectors.json", mapper, new TypeReference<>() { }),
                read(directory, "vehicles.json", mapper, new TypeReference<>() { }),
                read(directory, "charging_requests.json", mapper, new TypeReference<>() { }));
    }

    private static <T> List<T> read(Path directory, String file, JsonMapper mapper, TypeReference<List<T>> type) {
        Path path = directory.resolve(file);
        try {
            List<T> rows = mapper.readValue(Files.readString(path), type);
            return rows == null ? List.of() : rows;
        } catch (IOException e) {
            throw new DatasetValidationException(file + ": cannot be read from " + path.toAbsolutePath().normalize());
        } catch (JacksonException e) {
            throw new DatasetValidationException(file + ": invalid JSON (" + e.getOriginalMessage() + ")");
        }
    }
}
