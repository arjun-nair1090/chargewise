package com.chargewise.repository;

import com.chargewise.entity.AllocationRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationRunRepository extends JpaRepository<AllocationRun, Long> {

    Optional<AllocationRun> findFirstByOrderByCreatedAtDescIdDesc();
}
