import { useEffect } from 'react'
import { CircleMarker, MapContainer, TileLayer, Tooltip, useMap } from 'react-leaflet'
import { humanise } from '../lib/format'
import type { StationResponse, StationStatus } from '../types/api'

const STATUS_FILL: Record<StationStatus, string> = {
  OPERATIONAL: '#b8f34a',
  MAINTENANCE: '#f2c14e',
  OFFLINE: '#ff6b5e',
}

type Bounds = [[number, number], [number, number]]
const FIT = { padding: [32, 32] as [number, number], maxZoom: 13 }

/** The south-west and north-east corners around every station. */
function extent(stations: StationResponse[]): Bounds {
  let south = 90, west = 180, north = -90, east = -180
  for (const station of stations) {
    south = Math.min(south, station.latitude)
    north = Math.max(north, station.latitude)
    west = Math.min(west, station.longitude)
    east = Math.max(east, station.longitude)
  }
  return [[south, west], [north, east]]
}

/** Refits the view when the set of stations changes, e.g. when a state or city filter narrows it. */
function FitToStations({ bounds }: { bounds: Bounds }) {
  const map = useMap()
  const key = bounds.flat().join(',')
  useEffect(() => {
    const [south, west, north, east] = key.split(',').map(Number)
    map.fitBounds([[south, west], [north, east]], FIT)
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
  // The network spans India, so the view fits every station rather than centring on their mean.
  const bounds = extent(stations)
  // A country-wide view holds a couple of thousand markers: smaller dots keep cities legible at zoom 5.
  const dense = stations.length > 300
  return (
    <div aria-hidden="true" className="overflow-hidden rounded-lg border border-border" style={{ height }}>
      <MapContainer
        bounds={bounds}
        boundsOptions={FIT}
        scrollWheelZoom={false}
        keyboard={false}
        // Canvas draws thousands of markers as one layer instead of one SVG node each.
        preferCanvas
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
              radius={dense ? 3 + Math.min(3, station.connectors.length / 2) : 6 + Math.min(5, station.connectors.length)}
              pathOptions={{
                color: '#090b0a',
                weight: dense ? 1 : 2,
                fillColor: STATUS_FILL[station.status],
                fillOpacity: dim ? 0.3 : 0.95,
              }}
              eventHandlers={onSelect ? { click: () => onSelect(station) } : undefined}
            >
              <Tooltip direction="top" offset={[0, -8]}>
                <strong>{station.name}</strong>
                <br />
                {[station.city, station.state].filter(Boolean).join(', ')}
                <br />
                {humanise(station.status)} · {station.connectors.length} {station.connectors.length === 1 ? 'connector' : 'connectors'}
              </Tooltip>
            </CircleMarker>
          )
        })}
      </MapContainer>
    </div>
  )
}
