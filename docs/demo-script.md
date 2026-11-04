# Eight-minute demonstration script

This script follows the spec §17 timeline. Every number on screen comes from the running backend; nothing is
pre-recorded. Who speaks which part follows spec §16: Student 1 presents matching and the driver side, Student 2
presents flow and the operator side.

## Before the examiners arrive (about 5 minutes)

1. Start Docker Desktop, then run `docker compose up -d db` in the repository root.
2. Start the backend with seeding enabled:
   `cd backend && CHARGEWISE_DEMO_SEED_ON_STARTUP=true ./mvnw spring-boot:run`.
3. Start the frontend: `cd frontend && npm run dev`.
4. Open http://localhost:5173 in two browser windows:
   - one signed in as `operator@demo.chargewise.test`
   - one signed in as `driver01@demo.chargewise.test` (password: `CHARGEWISE_DEMO_PASSWORD` from your `.env`)
5. On the operator's **Benchmarks** page, check that the recorded runs (seed 20261103, all sizes) are in **Recent
   runs**. If the database was recreated, start one now with sizes Small and Medium only; it finishes in a few
   seconds.
6. On a projector, zoom the browser to 125%. The layouts are checked down to 390 px wide, so nothing breaks.

If anything was changed during rehearsal, go to **Stations → Reset demo data → Reload demo dataset**. This
restores the dataset and keeps benchmark and simulation history.

## 0:00–1:00 — The problem and the network (Student 2)

- **Operator → Overview.** Point at the network map, connectors free against in use, allocated power against grid
  capacity, and the waiting-time percentiles.
- **Say:** "Connectors are scarce and each station has a grid import limit. ChargeWise decides who charges where,
  and at what power, without ever breaking a limit."
- **Say:** "These are all 1,971 public car charging stations in India listed by Open Charge Map or OpenStreetMap.
  The drivers, bookings and grid limits are made up from a recorded seed, because nobody publishes those." Pick a
  state, then a city, to show the map refit.

## 1:00–2:00 — Driver: vehicle and charging request (Student 1)

1. **Driver → Garage → Add vehicle.** Enter a registration, battery size, state of charge, maximum kW, and tick
   CCS2.
2. **Find a station.** The map and list show only stations with a compatible connector. Open one: the
   availability timeline is computed per 15-minute slot.
3. Request a window and an energy amount, then submit. **My requests** shows it as *Pending*.
4. **Failure handling to show:** request more energy than the battery can take. The form rejects it with a field
   error, and the API returns the error contract's `VALIDATION_FAILED` code.

## 2:00–3:00 — Bipartite matching (Student 1)

1. **Operator → Allocation → Run allocation.**
2. Walk the outcome bar: assigned, waitlisted, rejected. **Every unassigned request has a reason**: *Lost
   contention*, *Connectors booked*, *No compatible connector*, and so on.
3. Open the evidence panel: edges examined, augmenting paths, and **reassignments**. Each reassignment is an earlier
   request that the algorithm moved to another connector so that a later one could fit, which greedy can never do.
4. **Algorithm Lab → Bipartite matching → Greedy trap → Execute.** Matching serves 2, greedy serves 1. Name the
   augmenting path `R2 → C1 → R1 → C2`.

## 3:00–4:00 — Edmonds-Karp power allocation (Student 2)

1. Back on the allocation run: power per station against its grid limit, with the station rows marked **Grid limit
   binds**. Those are the min-cut bottlenecks the algorithm reports.
2. **Say:** "On the spec's network every session hangs off one station, so the network is a tree. There, greedy
   reaches the same total, and we proved and measured that. Edmonds-Karp adds the min-cut certificate and the
   bottleneck diagnosis."
3. **Algorithm Lab → Edmonds-Karp → Shared feeders → Execute.** Edmonds-Karp serves 200 kW, greedy 100 kW. Greedy
   fed station A from F0 and stranded station B; the reverse residual edge re-routes A through F1.

## 4:00–5:00 — Randomized Quickselect: 90th-percentile waiting time (Student 1)

1. **Analytics.** p50, p75 and p90 of waiting time and demand, each labelled *Randomized Quickselect*, with the
   sample size.
2. **Algorithm Lab → Randomized Quickselect.** Run the example, then change the **seed** and run again. The
   pivots differ, the answer and the agreement with the full sort do not, and the comparison count changes.
3. **Say:** "Expected O(n). The seed is injected, so every run can be reproduced. Three-way partitioning keeps
   all-equal data to one pass."

## 5:00–6:00 — Outage and grid reduction, then re-run (Student 2)

1. **Stations.** Select a station that has assigned bookings.
   - Set its status to *Offline* and save. Its future assignments become *Waitlisted* (reason *Station outage*),
     and in-progress sessions become *Interrupted*.
   - Or take a single connector out of service with **Take out of service**.
2. On another station, lower **Grid import limit (kW)** and save. Edmonds-Karp re-allocates that station's live
   sessions in the same transaction; no slot can exceed the new limit.
3. **Allocation → Run allocation** again. Waitlisted requests are re-matched to other compatible connectors where
   one exists. The verifier reports **0 violations**.

## 6:00–7:00 — Benchmarks (both students)

1. **Benchmarks.** Open the recorded all-sizes run (seed 20261103): per-family tables of median and IQR, operation
   counts, quality on both sides, and **Spreads overlap** where the difference is within noise.
2. Three findings, all from measurement:
   - Quickselect is about 5× faster than sorting on uniform data at 100,000 values, and loses on presorted data,
     because the JDK sort exploits existing runs.
   - The segment tree is 89× faster than scanning at 100,000 values (JMH).
   - Matching costs O(V·E). At 10,000 requests and density 32 that is 1.4 s, and the work counter matches the
     `(L − R)·R·d` model.
3. Optional: show `docs/diagrams/benchmark-selection.png`, or press **Download JSON** to show the raw evidence.

## 7:00–8:00 — Summary and limitations (both students)

- **Results.** Zero capacity violations in every test, scenario and benchmark. Matching never serves fewer requests
  than greedy, and Edmonds-Karp never allocates less power. Every simulation re-run reproduces its input hash.
- **Limitations.**
  - One new booking per connector per allocation run.
  - Peak-based capacity reservation.
  - Edmonds-Karp delivers no extra power on the spec's tree-shaped network.
  - Made-up bookings and grid limits, and simulated sessions.
- **Objective, stated exactly.** Matching maximises assignments and max-flow maximises total power. Neither
  minimises waiting time or guarantees fairness.

## If the examiner changes the input

| Request | Where to go | What it shows |
|---|---|---|
| "Change the graph" | Algorithm Lab: edit the JSON, or **Generate random instance** with a new seed, or **Upload JSON** | Both sides recomputed on the server; agreement and verifier checks |
| "Show an edge case" | Algorithm Lab: an empty value list, an edge to an unknown connector, a negative capacity, or a percentile of 0 | A `VALIDATION_FAILED` message naming the field; never a wrong answer |
| "Infeasible network" | Algorithm Lab, Edmonds-Karp: set a station's capacity to 0 | Total 0, with the zero-capacity limit reported as the bottleneck |
| "Reproduce a simulation" | **Simulations** → select a run → **Re-run and verify** | `reproducedPreviousRun: true`: the same input hash and every non-timing metric |
| "Walk through the code" | `BipartiteMatcher.match`, `EdmondsKarp.maxFlow`, `RandomizedQuickselect.select`, `ChargingAvailabilitySegmentTree.rangeMax` | See [viva-notes.md](viva-notes.md) |
