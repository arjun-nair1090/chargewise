import type { Scenario } from '../types/api'

/** What each simulated scenario changes, in the operator's words. Numbers describe the server's presets. */
export const SCENARIO_TEXT: Record<Scenario, { label: string; description: string }> = {
  NORMAL: {
    label: 'Normal demand',
    description: 'By default 400 requests spread over one day across 10 stations of 4 connectors.',
  },
  PEAK_DEMAND: {
    label: 'Peak demand',
    description: 'Twice the requests, 70% of them arriving in the evening peak.',
  },
  CONNECTOR_OUTAGE: {
    label: 'Connector outage',
    description: 'About a quarter of the connectors fail for the middle half of the day; sessions on them are interrupted.',
  },
  STATION_OUTAGE: {
    label: 'Station outage',
    description: 'One station goes offline for the middle half of the day.',
  },
  GRID_REDUCTION: {
    label: 'Grid reduction',
    description: "Every station's grid-import limit is halved for the middle third of the day.",
  },
  MIXED_COMPATIBILITY: {
    label: 'Mixed compatibility',
    description: 'Connector and vehicle types are spread evenly, so many vehicles find no compatible connector.',
  },
  HIGH_CONTENTION: {
    label: 'High contention',
    description: 'By default 300 requests compete for 3 stations of 2 connectors.',
  },
  SHARED_FEEDER: {
    label: 'Shared feeders (extension D12)',
    description: 'Stations share distribution feeders sized at 40% of their limits, and some are fed from two feeders.',
  },
}
