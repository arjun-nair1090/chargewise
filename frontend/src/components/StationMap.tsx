import { CircleMarker, MapContainer, TileLayer, Tooltip } from 'react-leaflet'
import { humanise } from '../lib/format'
import type { StationResponse, StationStatus } from '../types/api'

const STATUS_FILL: Record<StationStatus, string> = {
  OPERATIONAL: '#b8f34a',
  MAINTENANCE: '#f2c14e',
  OFFLINE: '#ff6b5e',
}

/**
 * OpenStreetMap view of the synthetic network. The status word sits in each marker's tooltip, and the
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
  const bounds: [number, number][] = stations.map((station) => [station.latitude, station.longitude])
  return (
    <div aria-hidden="true" className="overflow-hidden rounded-lg border border-border" style={{ height }}>
      <MapContainer
        bounds={bounds}
        boundsOptions={{ padding: [28, 28], maxZoom: 13 }}
        scrollWheelZoom={false}
        keyboard={false}
        className="h-full w-full"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {stations.map((station) => {
          const dim = highlight ? !highlight(station) : false
          return (
            <CircleMarker
              key={station.id}
              center={[station.latitude, station.longitude]}
              radius={8}
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
