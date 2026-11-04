# Submission checklist

This checklist covers the spec §19 definition of done and the CIA 3 deliverables (guidelines §8). An item is ticked
only when its evidence exists and was checked on 2026-11-03. The two items left open need the team: real names and
IDs, Git history with attribution, and the viva itself.

## Spec §19 — Definition of done

- [x] **Driver and operator workflows work end-to-end.**
  - Integration tests drive the real REST API from booking through allocation, outage and grid reduction
    ([test-report.md](test-report.md), M4–M5).
  - A browser walkthrough of both portals was screenshotted ([screenshots/](screenshots/)).
  - A clean-environment run of the README did the same on a fresh database (M7).
- [x] **The Java backend executes all assessed algorithms.**
  - `backend/src/main/java/com/chargewise/algorithm/` contains them all.
  - `AlgorithmPurityTest` fails the build if they import Spring, Jakarta or Jackson.
  - The frontend only renders API responses ([architecture.md §1](architecture.md)).
- [x] **At least two distinct syllabus algorithms are central to the application.** Bipartite matching and
  Edmonds-Karp (Unit 5) decide every allocation run, and the segment tree (Unit 3) decides each station's available
  capacity ([architecture.md §6](architecture.md)).
- [x] **A Unit 4–6 technique is included.** Bipartite matching and Edmonds-Karp (Unit 5); Randomized Quickselect
  (Unit 6).
- [x] **The CO5 randomized component is implemented and evidenced.**
  - Randomized Quickselect with an injected, seeded generator, used for p50, p75 and p90.
  - Multi-seed and pivot-distribution tests ([algorithms.md §3](algorithms.md)).
  - Expected and worst-case analysis ([complexity-analysis.md §3](complexity-analysis.md)).
  - Benchmarks over uniform, sorted, reverse-sorted, duplicate-heavy and all-equal input
    ([benchmark-report.md §5](benchmark-report.md)).
- [x] **All algorithm tests pass, including edge cases.**
  - `./mvnw -B verify`: Surefire 189/189, Failsafe 86/86 (after the India-wide dataset change).
  - Each assessed algorithm has 10 or more tests, including edge, invalid and stress cases (M7).
- [x] **No connector or station power capacity constraint is violated.**
  - `CapacityVerifier` runs in every allocation; a violation rolls back the transaction.
  - A database exclusion constraint blocks overlapping reservations.
  - Zero violations across 8 scenarios × 5 seeds, and in every benchmark case
    ([benchmark-report.md](benchmark-report.md) tables, "Violations 0 / 0").
- [x] **Baselines are implemented and compared on identical inputs.**
  - Greedy first-available, greedy priority, full sort and naive scan.
  - Both sides receive the same generated input, and both are verified before timing
    ([benchmark-report.md §2](benchmark-report.md)).
- [x] **Three or more increasing benchmark sizes are tested.**
  - Small, Medium and Large (100, 1,000 and 10,000 requests).
  - Density, station count and distribution are varied independently.
  - Recorded in [benchmark-results/](benchmark-results/).
- [x] **Complexity analysis is documented.** See [complexity-analysis.md](complexity-analysis.md), cross-checked
  against measured operation counts in [benchmark-report.md](benchmark-report.md).
- [x] **Invalid and duplicate inputs are handled.**
  - The D8 error contract ([api-documentation.md](api-documentation.md)).
  - Validation integration tests: duplicate email and duplicate vehicle registration return `409`; an overlapping
    booking returns `409`; out-of-range fields return `400`.
  - The Algorithm Lab rejects duplicate node ids with `400`; duplicate graph edges are ignored by design.
  - The demo seeder refuses an invalid, duplicate or empty dataset with a clear error.
- [x] **Simulation results can be reproduced from the saved configuration and seed.**
  - `rerunReproducesAllNonTimingMetrics`
  - `SimulationIT.operatorExecutesSimulationAndRetrievesPersistedResults` reports `reproducedPreviousRun: true`
  - Benchmarks: the same seed reproduces every non-timing result (`BenchmarkIT`).
- [x] **The README setup instructions work on a clean environment.** On 2026-11-03 the README was followed in a
  fresh copy with no `node_modules`, no `target` and a new `.env`, on a new database (M7).
- [ ] **Both students can explain correctness, complexity and their contributions.** This is for the team to
  confirm. Preparation material: [viva-notes.md](viva-notes.md) and [demo-script.md](demo-script.md).

## Guidelines §8 — Deliverables

| Deliverable | Status | Where |
|---|---|---|
| A. Project proposal (1–2 pages) | Written; **team names and IDs to be filled in** | [project-proposal.md](project-proposal.md) |
| B. Source repository | Code, tests, sample data, build files and dependency manifests are present. The history so far is under one author. **Per spec §16 each student should commit their own work**, so attribution is the team's to complete | repository root |
| C. Technical report (5–8 pages) | Written; 8 A4 pages when exported to PDF (10.5 pt, 20 mm margins); team names and contributions table to be confirmed by the team | [technical-report.md](technical-report.md) |
| D. Demonstration evidence | Working local application, example inputs and outputs (Algorithm Lab worked examples), error handling, charts and tables | [demo-script.md](demo-script.md), [screenshots/](screenshots/), [diagrams/](diagrams/) |
| E. README and reproducibility | Prerequisites, build and run commands, environment variables (no secrets), API examples, dataset, screenshots | [README.md](../README.md) |

## Guidelines §11 — Rubric cross-reference

| Criterion (marks) | Evidence |
|---|---|
| Real-world problem formulation (10) | [project-proposal.md](project-proposal.md) §1–§2 and §6; [technical-report.md](technical-report.md) §1 |
| Algorithm selection and original implementation (20) | Four self-implemented algorithms with no library calls for the assessed logic; [algorithms.md](algorithms.md) |
| Algorithm correctness and test coverage (20) | Proofs in [algorithms.md](algorithms.md); oracle, mutation-checked and stress tests in [test-report.md](test-report.md) |
| Complexity and empirical performance (15) | [complexity-analysis.md](complexity-analysis.md); [benchmark-report.md](benchmark-report.md), with in-application and JMH runs and the losses kept |
| Java backend and API quality (10) | Layered Spring Boot, the D8 error contract, transactional pipeline; [api-documentation.md](api-documentation.md) |
| Frontend integration and usability (5) | Input → execute → interpret workflow in every operator page and the Algorithm Lab; [screenshots/](screenshots/) |
| Documentation and reproducibility (5) | README checked on a clean environment; seeded datasets, simulations and benchmarks |
| Live demo and individual viva (15) | [demo-script.md](demo-script.md), [viva-notes.md](viva-notes.md) |
