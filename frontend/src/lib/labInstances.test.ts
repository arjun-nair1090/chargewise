import { describe, expect, it } from 'vitest'
import { generateInstance, LAB_ALGORITHMS } from './labInstances'

describe('generateInstance', () => {
  it('gives the same instance for the same seed', () => {
    for (const algorithm of LAB_ALGORITHMS) {
      expect(generateInstance(algorithm, 2026), algorithm).toEqual(generateInstance(algorithm, 2026))
    }
  })

  it('gives a different instance for a different seed', () => {
    for (const algorithm of LAB_ALGORITHMS) {
      expect(generateInstance(algorithm, 1), algorithm).not.toEqual(generateInstance(algorithm, 2))
    }
  })

  it('keeps matching instances inside the server caps with unique ids and known endpoints', () => {
    const instance = generateInstance('matching', 7) as { requests: string[]; connectors: string[]; edges: { request: string; connector: string }[] }

    expect(new Set(instance.requests).size).toBe(instance.requests.length)
    expect(new Set(instance.connectors).size).toBe(instance.connectors.length)
    expect(instance.requests.length + instance.connectors.length).toBeLessThanOrEqual(5000)
    expect(instance.edges.length).toBeLessThanOrEqual(50_000)
    for (const edge of instance.edges) {
      expect(instance.requests).toContain(edge.request)
      expect(instance.connectors).toContain(edge.connector)
    }
  })

  it('keeps segment-tree queries inside the value range', () => {
    const instance = generateInstance('segment-tree', 3) as { values: number[]; operations: { type: string; from?: number; to?: number; index?: number }[] }

    expect(instance.operations.length).toBeLessThanOrEqual(1000)
    for (const operation of instance.operations) {
      if (operation.type === 'SUM' || operation.type === 'MAX') {
        expect(operation.from!).toBeLessThanOrEqual(operation.to!)
        expect(operation.to!).toBeLessThan(instance.values.length)
      } else {
        expect(operation.index!).toBeLessThan(instance.values.length)
      }
    }
  })

  it('produces a flow network whose source and sink are among its nodes', () => {
    const instance = generateInstance('max-flow', 5) as { network: { nodes: string[]; source: string; sink: string; edges: { from: string; to: string }[] } }

    expect(instance.network.nodes).toContain(instance.network.source)
    expect(instance.network.nodes).toContain(instance.network.sink)
    for (const edge of instance.network.edges) {
      expect(edge.from).not.toBe(edge.to)
    }
  })
})
