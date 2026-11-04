import type {
  AllocationRunResponse,
  AvailabilityResponse,
  BenchmarkRequest,
  BenchmarkRunResponse,
  BookingRequest,
  BookingResponse,
  BookingStatus,
  ConnectorRequest,
  ConnectorResponse,
  ConnectorStatus,
  DashboardResponse,
  LoginRequest,
  MatchingLabRequest,
  MatchingLabResponse,
  MaxFlowLabRequest,
  MaxFlowLabResponse,
  NearbyStation,
  Page,
  QuickselectLabRequest,
  QuickselectLabResponse,
  RegisterRequest,
  SegmentTreeLabRequest,
  SegmentTreeLabResponse,
  SessionResponse,
  SessionStatus,
  SimulationComparisonResponse,
  SimulationRequest,
  SimulationResponse,
  SimulationResultResponse,
  SimulationRunResponse,
  StationRequest,
  StationResponse,
  TokenResponse,
  UserResponse,
  VehicleRequest,
  VehicleResponse,
} from '../types/api'
import { apiFetch } from './api'

/** Typed wrappers over every endpoint the UI uses (docs/api-documentation.md). */

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const authApi = {
  login: (body: LoginRequest) => apiFetch<TokenResponse>('/auth/login', { method: 'POST', json: body }),
  register: (body: RegisterRequest) => apiFetch<UserResponse>('/auth/register', { method: 'POST', json: body }),
}

export const stationApi = {
  list: (page = 0, size = 100) => apiFetch<Page<StationResponse>>(`/stations${query({ page, size })}`),
  /** Every station in one response, for the network map and directory. */
  all: () => apiFetch<StationResponse[]>('/stations/all'),
  get: (id: number) => apiFetch<StationResponse>(`/stations/${id}`),
  nearby: (lat: number, lng: number, radiusKm: number) =>
    apiFetch<NearbyStation[]>(`/stations/nearby${query({ lat, lng, radiusKm })}`),
  availability: (id: number, from: string, to: string) =>
    apiFetch<AvailabilityResponse>(`/stations/${id}/availability${query({ from, to })}`),
}

export const vehicleApi = {
  list: () => apiFetch<VehicleResponse[]>('/vehicles'),
  create: (body: VehicleRequest) => apiFetch<VehicleResponse>('/vehicles', { method: 'POST', json: body }),
  update: (id: number, body: VehicleRequest) =>
    apiFetch<VehicleResponse>(`/vehicles/${id}`, { method: 'PUT', json: body }),
}

export const bookingApi = {
  list: (page = 0, size = 50) => apiFetch<Page<BookingResponse>>(`/bookings${query({ page, size })}`),
  get: (id: number) => apiFetch<BookingResponse>(`/bookings/${id}`),
  create: (body: BookingRequest) => apiFetch<BookingResponse>('/bookings', { method: 'POST', json: body }),
  cancel: (id: number) => apiFetch<BookingResponse>(`/bookings/${id}/cancel`, { method: 'PATCH' }),
}

export const sessionApi = {
  history: (page = 0, size = 50) => apiFetch<Page<SessionResponse>>(`/sessions/history${query({ page, size })}`),
  get: (id: number) => apiFetch<SessionResponse>(`/sessions/${id}`),
}

export const operatorApi = {
  dashboard: () => apiFetch<DashboardResponse>('/operator/dashboard'),
  requests: (status: BookingStatus | undefined, page = 0, size = 50) =>
    apiFetch<Page<BookingResponse>>(`/operator/requests${query({ status, page, size })}`),
  sessions: (status: SessionStatus | undefined, page = 0, size = 50) =>
    apiFetch<Page<SessionResponse>>(`/operator/sessions${query({ status, page, size })}`),
  runAllocation: () =>
    apiFetch<AllocationRunResponse>('/operator/allocation/run', { method: 'POST', json: {} }),
  allocation: (id: number) => apiFetch<AllocationRunResponse>(`/operator/allocation/${id}`),
  createStation: (body: StationRequest) =>
    apiFetch<StationResponse>('/operator/stations', { method: 'POST', json: body }),
  updateStation: (id: number, body: StationRequest) =>
    apiFetch<StationResponse>(`/operator/stations/${id}`, { method: 'PUT', json: body }),
  addConnector: (stationId: number, body: ConnectorRequest) =>
    apiFetch<ConnectorResponse>(`/operator/stations/${stationId}/connectors`, { method: 'POST', json: body }),
  setConnectorStatus: (id: number, status: ConnectorStatus) =>
    apiFetch<ConnectorResponse>(`/operator/connectors/${id}/status`, { method: 'PATCH', json: { status } }),
  resetDemo: () =>
    apiFetch<{ users: number; stations: number; connectors: number; vehicles: number; bookings: number }>(
      '/operator/demo/reset',
      { method: 'POST' },
    ),
}

export const labApi = {
  matching: (body: MatchingLabRequest) =>
    apiFetch<MatchingLabResponse>('/algorithms/matching/execute', { method: 'POST', json: body }),
  maxFlow: (body: MaxFlowLabRequest) =>
    apiFetch<MaxFlowLabResponse>('/algorithms/max-flow/execute', { method: 'POST', json: body }),
  quickselect: (body: QuickselectLabRequest) =>
    apiFetch<QuickselectLabResponse>('/algorithms/quickselect/execute', { method: 'POST', json: body }),
  segmentTree: (body: SegmentTreeLabRequest) =>
    apiFetch<SegmentTreeLabResponse>('/algorithms/segment-tree/query', { method: 'POST', json: body }),
}

export const simulationApi = {
  list: () => apiFetch<SimulationResponse[]>('/simulations'),
  create: (body: SimulationRequest) => apiFetch<SimulationResponse>('/simulations', { method: 'POST', json: body }),
  run: (id: number) => apiFetch<SimulationRunResponse>(`/simulations/${id}/run`, { method: 'POST' }),
  results: (id: number) => apiFetch<SimulationResultResponse[]>(`/simulations/${id}/results`),
  comparison: (id: number) => apiFetch<SimulationComparisonResponse>(`/simulations/${id}/comparison`),
}

export const benchmarkApi = {
  list: () => apiFetch<BenchmarkRunResponse[]>('/benchmarks'),
  run: (body: BenchmarkRequest) => apiFetch<BenchmarkRunResponse>('/benchmarks/run', { method: 'POST', json: body }),
  get: (id: number) => apiFetch<BenchmarkRunResponse>(`/benchmarks/${id}`),
}
