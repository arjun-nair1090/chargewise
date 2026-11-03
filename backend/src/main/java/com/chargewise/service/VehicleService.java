package com.chargewise.service;

import com.chargewise.dto.VehicleRequest;
import com.chargewise.dto.VehicleResponse;
import com.chargewise.entity.Vehicle;
import com.chargewise.exception.ApiException;
import com.chargewise.repository.UserRepository;
import com.chargewise.repository.VehicleRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Driver vehicles; every lookup is scoped to the caller in the repository query (D7). */
@Service
public class VehicleService {

    private static final String DUPLICATE = "A vehicle with this registration number already exists";

    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, UserRepository users, Clock clock) {
        this.vehicles = vehicles;
        this.users = users;
        this.clock = clock;
    }

    /** "mh-01 ab 1234" and "MH01AB1234" are the same plate. */
    static String normalizeRegistration(String raw) {
        String normalized = raw.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (normalized.isEmpty()) {
            throw ApiException.validation("registrationNumber", "must contain letters or digits");
        }
        return normalized;
    }

    @Transactional
    public VehicleResponse create(Long userId, VehicleRequest request) {
        String registration = normalizeRegistration(request.registrationNumber());
        if (vehicles.existsByRegistrationNumber(registration)) {
            throw ApiException.conflict(DUPLICATE);
        }
        Vehicle vehicle = vehicles.saveAndFlush(new Vehicle(users.getReferenceById(userId), registration,
                request.batteryCapacityKwh(), request.currentSoc(), request.maxChargingKw(),
                request.connectorTypes(), clock.instant()));
        return VehicleResponse.from(vehicle);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(Long userId) {
        return vehicles.findAllByUserIdOrderByIdAsc(userId).stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(Long userId, Long id) {
        return VehicleResponse.from(owned(userId, id));
    }

    @Transactional
    public VehicleResponse update(Long userId, Long id, VehicleRequest request) {
        Vehicle vehicle = owned(userId, id);
        String registration = normalizeRegistration(request.registrationNumber());
        if (!registration.equals(vehicle.getRegistrationNumber()) && vehicles.existsByRegistrationNumber(registration)) {
            throw ApiException.conflict(DUPLICATE);
        }
        vehicle.update(registration, request.batteryCapacityKwh(), request.currentSoc(), request.maxChargingKw(),
                request.connectorTypes());
        vehicles.flush();
        return VehicleResponse.from(vehicle);
    }

    Vehicle owned(Long userId, Long id) {
        return vehicles.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Vehicle"));
    }
}
