package com.chargewise.repository;

import com.chargewise.entity.BenchmarkRun;
import com.chargewise.entity.RunStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {

    List<BenchmarkRun> findTop20ByOrderByIdDesc();

    List<BenchmarkRun> findAllByStatusIn(Collection<RunStatus> statuses);
}
