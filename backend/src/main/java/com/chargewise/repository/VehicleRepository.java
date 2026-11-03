package com.chargewise.repository;

import com.chargewise.entity.Vehicle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Driver-facing lookups are always owner-scoped so one driver can never address another's vehicle. */
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByIdAndUserId(Long id, Long userId);

    List<Vehicle> findAllByUserIdOrderByIdAsc(Long userId);

    boolean existsByRegistrationNumber(String registrationNumber);
}
