/*
 * Response and request shapes of the ChargeWise REST API (docs/api-documentation.md).
 * kW and kWh values arrive as JSON numbers with at most two decimals (D1); timestamps are ISO-8601 UTC strings (D2).
 */

export type HealthResponse = {
  status: string
  database: string
  version: string
}

export type FieldError = {
  field: string
  message: string
}

/** Error body returned by every failing backend endpoint (architecture D8). */
export type ApiErrorBody = {
  status: number
  code: string
  message: string
  fieldErrors?: FieldError[]
  timestamp?: string
  path?: string
}

export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const ROLES = ['DRIVER', 'OPERATOR'] as const
export type Role = (typeof ROLES)[number]

export const CONNECTOR_TYPES = ['CCS2', 'TYPE2_AC', 'CHADEMO', 'BHARAT_DC001', 'BHARAT_AC001'] as const
export type ConnectorType = (typeof CONNECTOR_TYPES)[number]

export const STATION_STATUSES = ['OPERATIONAL', 'MAINTENANCE', 'OFFLINE'] as const
export type StationStatus = (typeof STATION_STATUSES)[number]

export const CONNECTOR_STATUSES = ['OPERATIONAL', 'OUT_OF_SERVICE'] as const
export type ConnectorStatus = (typeof CONNECTOR_STATUSES)[number]

export const BOOKING_STATUSES = ['PENDING', 'ASSIGNED', 'WAITLISTED', 'REJECTED', 'CANCELLED', 'COMPLETED'] as const
export type BookingStatus = (typeof BOOKING_STATUSES)[number]

export const SESSION_STATUSES = ['SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'INTERRUPTED'] as const
export type SessionStatus = (typeof SESSION_STATUSES)[number]

export const STATUS_REASONS = [
  'NO_COMPATIBLE_CONNECTOR_TYPE',
  'STATION_NOT_OPERATIONAL',
  'CONNECTORS_OUT_OF_SERVICE',
  'CONNECTORS_BOOKED_IN_WINDOW',
  'INSUFFICIENT_CONNECTOR_POWER',
  'LOST_CONTENTION',
  'WINDOW_EXPIRED',
  'ENERGY_EXCEEDS_BATTERY_HEADROOM',
  'CONNECTOR_OUTAGE',
  'STATION_OUTAGE',
  'INSUFFICIENT_GRID_CAPACITY',
] as const
export type StatusReason = (typeof STATUS_REASONS)[number]

export type BottleneckKind = 'FEEDER' | 'STATION_GRID' | 'CONNECTOR' | 'SESSION_DEMAND'

// --- Auth ---------------------------------------------------------------------------------------------------------

export type UserResponse = {
  id: number
  name: string
  email: string
  role: Role
  createdAt: string
}

export type TokenResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: UserResponse
}

export type LoginRequest = { email: string; password: string }
export type RegisterRequest = { name: string; email: string; password: string }

// --- Stations -----------------------------------------------------------------------------------------------------

export type ConnectorResponse = {
  id: number
  stationId: number
  connectorType: ConnectorType
  maxPowerKw: number
  status: ConnectorStatus
}

export type StationResponse = {
  id: number
  name: string
  address: string
  city: string | null
  state: string | null
  latitude: number
  longitude: number
  gridCapacityKw: number
  status: StationStatus
  connectors: ConnectorResponse[]
}

export type NearbyStation = { station: StationResponse; distanceKm: number }

export type SlotAvailability = {
  start: string
  end: string
  freeConnectors: number
  freeByType: Partial<Record<ConnectorType, number>>
}

export type AvailabilityResponse = {
  stationId: number
  from: string
  to: string
  slotMinutes: number
  operationalConnectors: number
  slots: SlotAvailability[]
}

export type StationRequest = {
  name: string
  address: string
  city: string | null
  state: string | null
  latitude: number
  longitude: number
  gridCapacityKw: number
  status: StationStatus
}

export type ConnectorRequest = {
  connectorType: ConnectorType
  maxPowerKw: number
  status: ConnectorStatus
}

// --- Vehicles -----------------------------------------------------------------------------------------------------

export type VehicleResponse = {
  id: number
  registrationNumber: string
  batteryCapacityKwh: number
  currentSoc: number
  maxChargingKw: number
  connectorTypes: ConnectorType[]
  createdAt: string
}

export type VehicleRequest = {
  registrationNumber: string
  batteryCapacityKwh: number
  currentSoc: number
  maxChargingKw: number
  connectorTypes: ConnectorType[]
}

// --- Bookings and sessions ----------------------------------------------------------------------------------------

export type BookingRequest = {
  vehicleId: number
  stationId: number
  requestedEnergyKwh: number
  requestedStart: string
  requestedEnd: string
}

export type BookingResponse = {
  id: number
  vehicleId: number
  vehicleRegistration: string
  stationId: number
  stationName: string
  connectorId: number | null
  connectorType: ConnectorType | null
  requestedEnergyKwh: number
  requestedStart: string
  requestedEnd: string
  status: BookingStatus
  statusReason: StatusReason | null
  sessionId: number | null
  allocatedPowerKw: number | null
  createdAt: string
  assignedAt: string | null
}

export type SessionResponse = {
  id: number
  bookingId: number
  stationId: number
  stationName: string
  connectorId: number
  connectorType: ConnectorType
  vehicleRegistration: string
  status: SessionStatus
  windowStart: string
  windowEnd: string
  startTime: string | null
  endTime: string | null
  requestedEnergyKwh: number
  energyDeliveredKwh: number
  allocatedPowerKw: number
}

// --- Operator -----------------------------------------------------------------------------------------------------

export type DashboardResponse = {
  stations: Record<StationStatus, number>
  connectors: Record<ConnectorStatus, number>
  bookings: Record<BookingStatus, number>
  sessions: Record<SessionStatus, number>
  utilisation: {
    activeSessions: number
    operationalConnectors: number
    connectorUtilisation: number | null
    activePowerKw: number
    operationalGridKw: number
    powerUtilisation: number | null
  }
  waiting: {
    algorithm: string
    assignedSampleSize: number
    p50AssignWaitSeconds: number | null
    p90AssignWaitSeconds: number | null
    queueSize: number
    p50QueueAgeSeconds: number | null
    p90QueueAgeSeconds: number | null
  }
  latestAllocationRunId: number | null
  generatedAt: string
}

export type Assignment = {
  bookingId: number
  stationId: number
  connectorId: number
  sessionId: number
  requestedKw: number
  allocatedKw: number
}

export type Unassigned = {
  bookingId: number
  stationId: number
  status: BookingStatus
  reason: StatusReason
}

/** The last three fields are null when no request was matched at the station, so it had no power problem to solve. */
export type StationCapacity = {
  stationId: number
  gridKw: number
  demandKw: number
  demandCapacityRatio: number | null
  committedPeakKw: number | null
  availableKw: number | null
  allocatedKw: number | null
}

export type BottleneckView = {
  kind: BottleneckKind
  stationId: number | null
  bookingId: number | null
  capacityKw: number
}

export type AllocationRunResult = {
  matchingAlgorithm: string
  powerAlgorithm: string
  selectionAlgorithm: string
  eligibleRequests: number
  assignedCount: number
  waitlistedCount: number
  rejectedCount: number
  assignments: Assignment[]
  unassigned: Unassigned[]
  stations: StationCapacity[]
  bottlenecks: BottleneckView[]
  requestedPower: {
    p50Kw: number
    p75Kw: number
    p90Kw: number
    seed: number
    comparisons: number
    swaps: number
  } | null
  metrics: {
    compatibilityEdges: number
    matchingCardinality: number
    edgesExamined: number
    augmentations: number
    flowOperations: number
    totalRequestedKw: number
    totalAllocatedKw: number
    unservedKw: number
    selectionNs: number
    matchingNs: number
    powerNs: number
    totalNs: number
  }
}

export type AllocationRunResponse = {
  id: number
  intervalStart: string
  intervalEnd: string
  createdAt: string
  result: AllocationRunResult
}

// --- Algorithm Lab ------------------------------------------------------------------------------------------------

export type MatchingLabRequest = {
  requests: string[]
  connectors: string[]
  edges: { request: string; connector: string }[]
}

export type MatchingRun = {
  name: string
  cardinality: number
  pairs: { request: string; connector: string }[]
  unmatched: string[]
  edgesExamined: number
  augmentations: number
  reassignments: number
  valid: boolean
  runtimeNs: number
}

export type MatchingLabResponse = { algorithm: MatchingRun; baseline: MatchingRun }

export type FlowNetworkInput = {
  nodes: string[]
  edges: { from: string; to: string; capacity: number }[]
  source: string
  sink: string
}

export type PowerNetworkInput = {
  stations: { id: string; capacityKw: number }[]
  sessions: { id: string; station: string; connectorMaxKw: number; requestedKw: number; vehicleMaxKw: number }[]
  feeders?: { id: string; capacityKw: number }[]
  links?: { feeder: string; station: string }[]
}

export type MaxFlowLabRequest = { network: FlowNetworkInput } | { power: PowerNetworkInput }

export type EdgeFlow = { from: string; to: string; capacity: number; flow: number }

export type PowerRun = {
  name: string
  totalRequestedKw: number
  totalAllocatedKw: number
  unservedKw: number
  stations: { id: string; allocatedKw: number; remainingKw: number }[]
  sessions: { id: string; station: string; allocatedKw: number }[]
  bottlenecks: { kind: BottleneckKind; id: string; capacityKw: number }[]
  operations: number
  violations: string[]
  runtimeNs: number
}

export type MaxFlowLabResponse = {
  mode: 'NETWORK' | 'POWER'
  network: {
    algorithm: string
    totalFlow: number
    minCutCapacity: number
    edges: EdgeFlow[]
    minCut: EdgeFlow[]
    augmentations: number
    bfsRuns: number
    edgesScanned: number
    runtimeNs: number
  } | null
  power: { algorithm: PowerRun; baseline: PowerRun } | null
}

export type QuickselectLabRequest = {
  values: number[]
  k?: number
  percentiles?: number[]
  seed?: number
}

export type SelectionWork = {
  name: string
  comparisons: number
  swaps: number
  partitionRounds: number
  runtimeNs: number
}

export type QuickselectLabResponse = {
  n: number
  seed: number
  agree: boolean
  selections: { label: string; rank: number; value: number; baselineValue: number }[]
  algorithm: SelectionWork
  baseline: SelectionWork
}

export type TreeOperationType = 'SUM' | 'MAX' | 'SET' | 'ADD'

export type TreeOperation =
  | { type: 'SUM' | 'MAX'; from: number; to: number }
  | { type: 'SET' | 'ADD'; index: number; value: number }

export type SegmentTreeLabRequest = { values: number[]; operations: TreeOperation[] }

export type SegmentTreeLabResponse = {
  n: number
  operationCount: number
  agree: boolean
  results: { operation: number; type: TreeOperationType; from: number; to: number; tree: number; naive: number }[]
  algorithm: { name: string; runtimeNs: number }
  baseline: { name: string; runtimeNs: number }
}

// Simulations (architecture D9/D10). Power totals are kW summed over 15-minute slots; energy is kWh.

export const SCENARIOS = [
  'NORMAL',
  'PEAK_DEMAND',
  'CONNECTOR_OUTAGE',
  'STATION_OUTAGE',
  'GRID_REDUCTION',
  'MIXED_COMPATIBILITY',
  'HIGH_CONTENTION',
  'SHARED_FEEDER',
] as const
export type Scenario = (typeof SCENARIOS)[number]

export type RunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type Strategy = 'ALGORITHM' | 'BASELINE'

export type SimulationConfig = {
  scenario: Scenario
  stations: number
  connectorsPerStation: number
  requests: number
  horizonSlots: number
  maxWaitSlots: number
}

export type SimulationRequest = {
  name: string
  scenario: Scenario
  seed: number
  stations?: number
  connectorsPerStation?: number
  requests?: number
  horizonSlots?: number
  maxWaitSlots?: number
}

export type SimulationResponse = {
  id: number
  name: string
  seed: number
  configuration: SimulationConfig
  status: RunStatus
  createdAt: string
}

export type SimulationRunResponse = {
  simulation: SimulationResponse
  inputHash: string
  /** Absent on the first run; on a re-run, whether the input hash and every non-timing metric matched. */
  reproducedPreviousRun?: boolean
}

export type SimulationMetrics = {
  strategy: Strategy
  matchingAlgorithm: string
  powerAlgorithm: string
  requestCount: number
  matchedCount: number
  unmatchedCount: number
  expiredCount: number
  pendingAtHorizonEnd: number
  completedSessions: number
  interruptedSessions: number
  requestedEnergyKwh: number
  deliveredEnergyKwh: number
  totalRequestedKw: number
  totalAllocatedKw: number
  unservedKw: number
  stationUtilisation: number
  stationUtilisationByStation: number[]
  waitSampleSize: number
  waitP50Minutes: number | null
  waitP90Minutes: number | null
  capacityViolations: number
  matchingViolations: number
  slotsWhereBaselineMatchedMore: number
  slotsWhereBaselineAllocatedMore: number
  matchedGainOverBaseline: number
  allocatedGainOverBaselineKw: number
  edgesExamined: number
  powerOperations: number
  matchingNs: number
  powerNs: number
  totalNs: number
}

export type SimulationResultResponse = {
  id: number
  strategy: Strategy
  algorithmName: string
  algorithmVersion: string
  inputHash: string
  metrics: SimulationMetrics
  runtimeNs: number | null
  createdAt: string
}

export type ComparisonRow = {
  metric: string
  label: string
  unit: string
  algorithm: number | null
  baseline: number | null
  difference: number | null
  betterWhen: 'HIGHER' | 'LOWER'
}

export type SimulationComparisonResponse = {
  simulationId: number
  name: string
  scenario: Scenario
  seed: number
  inputHash: string
  algorithm: SimulationMetrics
  baseline: SimulationMetrics
  rows: ComparisonRow[]
}

// Benchmarks (project brief §10). Times are nanoseconds; quartiles are nearest-rank over the timed trials.

export const BENCHMARK_SIZES = ['SMALL', 'MEDIUM', 'LARGE'] as const
export type BenchmarkSize = (typeof BENCHMARK_SIZES)[number]

export const BENCHMARK_FAMILIES = ['MATCHING', 'POWER', 'POWER_FEEDERS', 'SELECTION', 'RANGE_QUERY'] as const
export type BenchmarkFamily = (typeof BENCHMARK_FAMILIES)[number]

export type BenchmarkConfig = {
  seed: number
  warmups: number
  trials: number
  sizes: BenchmarkSize[]
  families: BenchmarkFamily[]
}

export type BenchmarkRequest = {
  seed: number
  warmups?: number
  trials?: number
  sizes: BenchmarkSize[]
  families?: BenchmarkFamily[]
}

export type BenchmarkMeasurement = {
  name: string
  trials: number
  medianNs: number
  q1Ns: number
  q3Ns: number
  iqrNs: number
  minNs: number
  maxNs: number
  /** The implementation's own work counter per trial; -1 where it has none. */
  operationsMedian: number
  operationsMin: number
  operationsMax: number
  /** Bytes allocated by one extra run; null where the JVM cannot count them. */
  allocatedBytes: number | null
}

export type BenchmarkCase = {
  family: BenchmarkFamily
  label: string
  size: BenchmarkSize
  parameters: Record<string, number | string>
  /** Both sides verified correct on the input; an unverified case carries no measurements. */
  correct: boolean
  algorithm: BenchmarkMeasurement | null
  baseline: BenchmarkMeasurement | null
  algorithmQuality: number
  baselineQuality: number
  qualityUnit: string
  outcomes: Record<string, number>
}

export type BenchmarkReport = BenchmarkConfig & {
  environment: {
    javaVersion: string
    javaVendor: string
    vmName: string
    availableProcessors: number
    maxHeapBytes: number
    osName: string
    osArch: string
  }
  cases: BenchmarkCase[]
  totalNs: number
}

export type BenchmarkRunResponse = {
  id: number
  seed: number
  configuration: BenchmarkConfig
  status: RunStatus
  createdAt: string
  /** Present on a single completed run; lists leave it out. */
  report?: BenchmarkReport
}
