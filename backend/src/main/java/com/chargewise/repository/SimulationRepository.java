package com.chargewise.repository;

import com.chargewise.entity.Simulation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SimulationRepository extends JpaRepository<Simulation, Long> {

    /** Serialises runs of one simulation: a second run waits until the first has stored its results. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Simulation s where s.id = :id")
    Optional<Simulation> findByIdForUpdate(@Param("id") Long id);

    List<Simulation> findTop50ByOrderByIdDesc();
}
