import type { ConnectorType, StationResponse, VehicleResponse } from '../../types/api'

/** Display filter only: does the station have an operational connector of one of the vehicle's types? */
export function isCompatible(station: StationResponse, vehicle: VehicleResponse | undefined): boolean {
  if (!vehicle) {
    return true
  }
  return station.connectors.some(
    (connector) => connector.status === 'OPERATIONAL' && vehicle.connectorTypes.includes(connector.connectorType),
  )
}

/** "CCS2 ×3 · Type 2 AC ×2": connector counts by type, in a stable order. */
export function connectorSummary(station: StationResponse): { type: ConnectorType; count: number; maxKw: number }[] {
  const byType = new Map<ConnectorType, { count: number; maxKw: number }>()
  for (const connector of station.connectors) {
    const entry = byType.get(connector.connectorType) ?? { count: 0, maxKw: 0 }
    entry.count += 1
    entry.maxKw = Math.max(entry.maxKw, connector.maxPowerKw)
    byType.set(connector.connectorType, entry)
  }
  return [...byType.entries()]
    .map(([type, entry]) => ({ type, ...entry }))
    .sort((left, right) => left.type.localeCompare(right.type))
}
