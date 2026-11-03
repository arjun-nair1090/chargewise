package com.chargewise.service;

import com.chargewise.benchmark.BenchmarkConfig;
import com.chargewise.benchmark.BenchmarkFamily;
import com.chargewise.benchmark.BenchmarkReport;
import com.chargewise.benchmark.BenchmarkRunner;
import com.chargewise.dto.benchmark.BenchmarkRequest;
import com.chargewise.dto.benchmark.BenchmarkRunResponse;
import com.chargewise.entity.BenchmarkRun;
import com.chargewise.entity.RunStatus;
import com.chargewise.exception.ApiException;
import com.chargewise.repository.BenchmarkRunRepository;
import com.chargewise.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts benchmark runs on the single benchmark thread and stores each measured report (project brief §10). A run is
 * stored as PENDING, moves to RUNNING when its turn comes, and ends COMPLETED with its report or FAILED. The timed work
 * runs outside any database transaction; only the short status changes take one.
 */
@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final BenchmarkRunRepository runs;
    private final UserRepository users;
    private final JsonMapper json;
    private final Clock clock;
    private final TaskExecutor executor;
    private final TransactionTemplate transaction;

    public BenchmarkService(BenchmarkRunRepository runs, UserRepository users, JsonMapper json, Clock clock,
            @Qualifier("benchmarkExecutor") TaskExecutor executor, TransactionTemplate transaction) {
        this.runs = runs;
        this.users = users;
        this.json = json;
        this.clock = clock;
        this.executor = executor;
        this.transaction = transaction;
    }

    public BenchmarkRunResponse start(BenchmarkRequest request, Long operatorId) {
        BenchmarkConfig config = new BenchmarkConfig(request.seed(),
                request.warmups() == null ? BenchmarkRequest.DEFAULT_WARMUPS : request.warmups(),
                request.trials() == null ? BenchmarkRequest.DEFAULT_TRIALS : request.trials(), request.sizes(),
                request.families() == null ? List.of(BenchmarkFamily.values()) : request.families());
        BenchmarkRun run = transaction.execute(status -> runs.save(new BenchmarkRun(users.getReferenceById(operatorId),
                config.seed(), json.writeValueAsString(config), RunStatus.PENDING, Instant.now(clock))));
        long id = run.getId();
        try {
            executor.execute(() -> execute(id, config));
        } catch (TaskRejectedException e) {
            transaction.executeWithoutResult(status -> runs.deleteById(id));
            throw ApiException.conflict("Benchmark queue is full; wait for a queued run to finish");
        }
        return toResponse(run, null);
    }

    @Transactional(readOnly = true)
    public BenchmarkRunResponse get(Long id) {
        BenchmarkRun run = runs.findById(id).orElseThrow(() -> ApiException.notFound("Benchmark run"));
        BenchmarkReport report = run.getResults() == null ? null
                : json.readValue(run.getResults(), BenchmarkReport.class);
        return toResponse(run, report);
    }

    @Transactional(readOnly = true)
    public List<BenchmarkRunResponse> list() {
        return runs.findTop20ByOrderByIdDesc().stream().map(run -> toResponse(run, null)).toList();
    }

    /** A run left PENDING or RUNNING by a previous process will never finish; record it as failed. */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedRuns() {
        transaction.executeWithoutResult(status ->
                runs.findAllByStatusIn(List.of(RunStatus.PENDING, RunStatus.RUNNING)).forEach(BenchmarkRun::fail));
    }

    private void execute(long id, BenchmarkConfig config) {
        if (!transition(id, BenchmarkRun::markRunning)) {
            return; // deleted while queued
        }
        try {
            String results = json.writeValueAsString(BenchmarkRunner.run(config));
            transition(id, run -> run.complete(results));
        } catch (RuntimeException e) {
            log.error("Benchmark run {} failed", id, e);
            transition(id, BenchmarkRun::fail);
        }
    }

    /** Applies one status change in its own transaction; false when the run no longer exists. */
    private boolean transition(long id, Consumer<BenchmarkRun> change) {
        return Boolean.TRUE.equals(transaction.execute(status -> runs.findById(id).map(run -> {
            change.accept(run);
            return true;
        }).orElse(false)));
    }

    private BenchmarkRunResponse toResponse(BenchmarkRun run, BenchmarkReport report) {
        return new BenchmarkRunResponse(run.getId(), run.getRandomSeed(),
                json.readValue(run.getConfiguration(), BenchmarkConfig.class), run.getStatus(), run.getCreatedAt(),
                report);
    }
}
