package com.chargewise.repository;

import com.chargewise.entity.SimulationResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationResultRepository extends JpaRepository<SimulationResult, Long> {

    List<SimulationResult> findAllBySimulationIdOrderByIdAsc(Long simulationId);
}
