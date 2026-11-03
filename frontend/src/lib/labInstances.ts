import type {
  MatchingLabRequest,
  MaxFlowLabRequest,
  QuickselectLabRequest,
  SegmentTreeLabRequest,
  TreeOperation,
} from '../types/api'

/**
 * Seeded random INPUTS for the Algorithm Lab. These only generate instances to send to the server; every result
 * shown on the page is computed by the Java algorithms.
 */

export const LAB_ALGORITHMS = ['matching', 'max-flow', 'quickselect', 'segment-tree'] as const
export type LabAlgorithm = (typeof LAB_ALGORITHMS)[number]

/** mulberry32: a small, well-known 32-bit PRNG; the same seed always yields the same sequence. */
function prng(seed: number) {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4_294_967_296
  }
}

function intBetween(random: () => number, min: number, max: number): number {
  return min + Math.floor(random() * (max - min + 1))
}

function matching(seed: number): MatchingLabRequest {
  const random = prng(seed)
  const requests = Array.from({ length: 12 }, (_, index) => `R${index + 1}`)
  const connectors = Array.from({ length: 9 }, (_, index) => `C${index + 1}`)
  const edges: MatchingLabRequest['edges'] = []
  for (const request of requests) {
    for (const connector of connectors) {
      if (random() < 0.22) {
        edges.push({ request, connector })
      }
    }
  }
  return { requests, connectors, edges }
}

function maxFlow(seed: number): MaxFlowLabRequest {
  const random = prng(seed)
  const middle = Array.from({ length: 6 }, (_, index) => `v${index + 1}`)
  const nodes = ['s', ...middle, 't']
  const edges: { from: string; to: string; capacity: number }[] = []
  for (const node of middle) {
    if (random() < 0.7) {
      edges.push({ from: 's', to: node, capacity: intBetween(random, 4, 20) })
    }
    if (random() < 0.7) {
      edges.push({ from: node, to: 't', capacity: intBetween(random, 4, 20) })
    }
  }
  for (const from of middle) {
    for (const to of middle) {
      if (from !== to && random() < 0.25) {
        edges.push({ from, to, capacity: intBetween(random, 1, 15) })
      }
    }
  }
  return { network: { nodes, edges, source: 's', sink: 't' } }
}

function quickselect(seed: number): QuickselectLabRequest {
  const random = prng(seed)
  const values = Array.from({ length: 2000 }, () => intBetween(random, 0, 100_000))
  return { values, percentiles: [50, 75, 90], seed }
}

function segmentTree(seed: number): SegmentTreeLabRequest {
  const random = prng(seed)
  const size = 96
  const values = Array.from({ length: size }, () => intBetween(random, 0, 8))
  const operations: TreeOperation[] = []
  for (let index = 0; index < 24; index += 1) {
    if (random() < 0.7) {
      const from = intBetween(random, 0, size - 1)
      const to = intBetween(random, from, size - 1)
      operations.push({ type: random() < 0.5 ? 'SUM' : 'MAX', from, to })
    } else {
      operations.push({ type: random() < 0.5 ? 'SET' : 'ADD', index: intBetween(random, 0, size - 1), value: intBetween(random, 0, 8) })
    }
  }
  return { values, operations }
}

export function generateInstance(algorithm: LabAlgorithm, seed: number): unknown {
  switch (algorithm) {
    case 'matching':
      return matching(seed)
    case 'max-flow':
      return maxFlow(seed)
    case 'quickselect':
      return quickselect(seed)
    case 'segment-tree':
      return segmentTree(seed)
  }
}

/** Worked examples from docs/api-documentation.md, so an examiner can check a known answer. */
export const LAB_EXAMPLES: Record<LabAlgorithm, { label: string; input: unknown }[]> = {
  matching: [
    {
      label: 'Greedy trap (matching 2, greedy 1)',
      input: { requests: ['R1', 'R2'], connectors: ['C1', 'C2'], edges: [{ request: 'R1', connector: 'C1' }, { request: 'R1', connector: 'C2' }, { request: 'R2', connector: 'C1' }] },
    },
  ],
  'max-flow': [
    {
      label: 'CLRS Figure 26.1 (flow 23)',
      input: {
        network: {
          nodes: ['s', 'v1', 'v2', 'v3', 'v4', 't'],
          source: 's',
          sink: 't',
          edges: [
            { from: 's', to: 'v1', capacity: 16 }, { from: 's', to: 'v2', capacity: 13 },
            { from: 'v1', to: 'v3', capacity: 12 }, { from: 'v2', to: 'v1', capacity: 4 },
            { from: 'v2', to: 'v4', capacity: 14 }, { from: 'v3', to: 'v2', capacity: 9 },
            { from: 'v3', to: 't', capacity: 20 }, { from: 'v4', to: 'v3', capacity: 7 },
            { from: 'v4', to: 't', capacity: 4 },
          ],
        },
      },
    },
    {
      label: 'Shared feeders (Edmonds-Karp 200 kW, greedy 100 kW)',
      input: {
        power: {
          stations: [{ id: 'A', capacityKw: 100 }, { id: 'B', capacityKw: 100 }],
          sessions: [
            { id: 'a1', station: 'A', connectorMaxKw: 100, requestedKw: 100, vehicleMaxKw: 100 },
            { id: 'b1', station: 'B', connectorMaxKw: 100, requestedKw: 100, vehicleMaxKw: 100 },
          ],
          feeders: [{ id: 'F0', capacityKw: 100 }, { id: 'F1', capacityKw: 100 }],
          links: [{ feeder: 'F0', station: 'A' }, { feeder: 'F0', station: 'B' }, { feeder: 'F1', station: 'A' }],
        },
      },
    },
  ],
  quickselect: [
    { label: 'Small list, median', input: { values: [9, 2, 7, 4, 5, 1, 8], percentiles: [50], seed: 42 } },
  ],
  'segment-tree': [
    {
      label: 'Booked connectors over 8 slots',
      input: {
        values: [1, 3, 2, 4, 0, 2, 5, 1],
        operations: [
          { type: 'MAX', from: 0, to: 7 },
          { type: 'SUM', from: 2, to: 5 },
          { type: 'ADD', index: 4, value: 3 },
          { type: 'MAX', from: 3, to: 5 },
        ],
      },
    },
  ],
}
