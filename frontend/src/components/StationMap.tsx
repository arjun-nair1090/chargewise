import { useEffect } from 'react'
import { CircleMarker, MapContainer, TileLayer, Tooltip, useMap } from 'react-leaflet'
import { humanise } from '../lib/format'
import type { StationResponse, StationStatus } from '../types/api'

const STATUS_FILL: Record<StationStatus, string> = {
  OPERATIONAL: '#b8f34a',
  MAINTENANCE: '#f2c14e',
  OFFLINE: '#ff6b5e',
}

type Bounds = [number, number][]
const FIT = { padding: [32, 32] as [number, number], maxZoom: 13 }

/** Refits the view when the set of stations changes, e.g. when a region filter narrows it. */
function FitToStations({ bounds }: { bounds: Bounds }) {
  const map = useMap()
  const key = JSON.stringify(bounds)
  useEffect(() => {
    map.fitBounds(JSON.parse(key) as Bounds, FIT)
  }, [map, key])
  return null
}

/**
 * OpenStreetMap view of the station network. The status word sits in each marker's tooltip, and the
 * station list beside the map carries the same information for keyboard and screen-reader users.
 */
export function StationMap({ stations, onSelect, height = '24rem', highlight }: {
  stations: StationResponse[]
  onSelect?: (station: StationResponse) => void
  height?: string
  highlight?: (station: StationResponse) => boolean
}) {
  if (stations.length === 0) {
    return null
  }
  // The network spans more than one city, so the view fits every station rather than centring on their mean.
  const bounds: Bounds = stations.map((station) => [station.latitude, station.longitude])
  return (
    <div aria-hidden="true" className="overflow-hidden rounded-lg border border-border" style={{ height }}>
      <MapContainer
        bounds={bounds}
        boundsOptions={FIT}
        scrollWheelZoom={false}
        keyboard={false}
        className="h-full w-full"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <FitToStations bounds={bounds} />
        {stations.map((station) => {
          const dim = highlight ? !highlight(station) : false
          return (
            <CircleMarker
              key={station.id}
              center={[station.latitude, station.longitude]}
              // Marker size grows with the station's connector count (capped), so large hubs read first.
              radius={6 + Math.min(5, station.connectors.length)}
              pathOptions={{
                color: '#090b0a',
                weight: 2,
                fillColor: STATUS_FILL[station.status],
                fillOpacity: dim ? 0.3 : 0.95,
              }}
              eventHandlers={onSelect ? { click: () => onSelect(station) } : undefined}
            >
              <Tooltip direction="top" offset={[0, -8]}>
                <strong>{station.name}</strong>
                <br />
                {humanise(station.status)} · {station.connectors.length} connectors
              </Tooltip>
            </CircleMarker>
          )
        })}
      </MapContainer>
    </div>
  )
}
