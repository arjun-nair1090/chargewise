package com.chargewise.repository;

import com.chargewise.entity.PowerAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PowerAllocationRepository extends JpaRepository<PowerAllocation, Long> {

    List<PowerAllocation> findAllBySessionIdOrderByAllocationTimeAsc(Long sessionId);
}
