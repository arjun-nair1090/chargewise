import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { SelectField } from '../../components/Field'
import { QueryState } from '../../components/QueryState'
import { RegionSwitch } from '../../components/RegionSwitch'
import { StationMap } from '../../components/StationMap'
import { StatusChip } from '../../components/StatusChip'
import { Button, EmptyState, PageHeader, Panel } from '../../components/ui'
import { errorMessage } from '../../lib/errors'
import { connectorLabel, formatNumber } from '../../lib/format'
import { inRegion, REGIONS, type Region } from '../../lib/regions'
import { stationApi, vehicleApi } from '../../services/endpoints'
import type { StationResponse } from '../../types/api'
import { connectorSummary, isCompatible } from './stationHelpers'

const NEARBY_RADIUS_KM = 25

type Position = { lat: number; lng: number }

function StationRow({ station, distanceKm, compatible, vehicleParam }: {
  station: StationResponse
  distanceKm?: number
  compatible: boolean
  vehicleParam: string
}) {
  const summary = connectorSummary(station)
  const fastestKw = Math.max(0, ...summary.map((entry) => entry.maxKw))
  return (
    <li className="border-b border-border last:border-b-0">
      <Link
        to={`/driver/stations/${station.id}${vehicleParam ? `?vehicle=${vehicleParam}` : ''}`}
        className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-5 px-5 py-4 transition-colors duration-150 hover:bg-surface-raised"
      >
        <div className={`min-w-0 ${vehicleParam && !compatible ? 'opacity-60' : ''}`}>
          <p className="font-medium">{station.name}</p>
          <p className="mt-0.5 text-sm text-muted">{station.address}</p>
          <div className="mt-2.5 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm">
            <StatusChip status={station.status} />
            {vehicleParam && (
              <span className={compatible ? 'font-medium text-text' : 'text-muted'}>
                {compatible ? 'Compatible' : 'No compatible connector'}
              </span>
            )}
          </div>
          <ul aria-label="Connectors" className="mt-2.5 flex flex-wrap gap-1.5">
            {summary.map((entry) => (
              <li key={entry.type} className="inline-flex h-6 items-center gap-1.5 rounded-[4px] border border-border-strong px-2 text-xs text-muted">
                <span className="text-text">{connectorLabel(entry.type)}</span>
                <span className="font-mono">×{entry.count}</span>
              </li>
            ))}
          </ul>
        </div>
        <div className="flex flex-col items-end text-right">
          <span className="text-xs text-muted">Fastest</span>
          <span className="mt-0.5 text-xl font-semibold leading-tight tracking-[-0.02em]">
            {formatNumber(fastestKw)}
            <span className="ml-1 font-mono text-xs font-normal text-muted">kW</span>
          </span>
          {distanceKm !== undefined && <span className="mt-2 text-sm text-muted">{formatNumber(distanceKm)} km</span>}
        </div>
      </Link>
    </li>
  )
}

export function StationsPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const vehicleParam = params.get('vehicle') ?? ''
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: vehicleApi.list })
  const stations = useQuery({ queryKey: ['stations'], queryFn: () => stationApi.list() })
  const [position, setPosition] = useState<Position | null>(null)
  const [region, setRegion] = useState<Region>('ALL')
  const [locating, setLocating] = useState(false)
  const [locationError, setLocationError] = useState<string | null>(null)
  const nearby = useQuery({
    queryKey: ['stations', 'nearby', position],
    queryFn: () => stationApi.nearby(position!.lat, position!.lng, NEARBY_RADIUS_KM),
    enabled: position !== null,
  })

  const vehicle = vehicles.data?.find((candidate) => String(candidate.id) === vehicleParam)

  function locate() {
    if (!('geolocation' in navigator)) {
      setLocationError('This browser cannot share its location.')
      return
    }
    setLocating(true)
    setLocationError(null)
    navigator.geolocation.getCurrentPosition(
      (result) => {
        setLocating(false)
        setPosition({ lat: result.coords.latitude, lng: result.coords.longitude })
      },
      () => {
        setLocating(false)
        setLocationError('Location permission was refused, so all stations are listed.')
      },
      { timeout: 10_000 },
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Find a station"
        job="Pick a station with a connector your vehicle can use, then request a charging window there."
      />

      <div className="flex flex-wrap items-end gap-4">
        <div className="w-full sm:w-64">
          <SelectField
            label="Compatible with"
            value={vehicleParam}
            onChange={(event) => setParams(event.target.value ? { vehicle: event.target.value } : {}, { replace: true })}
          >
            <option value="">Any vehicle</option>
            {vehicles.data?.map((candidate) => (
              <option key={candidate.id} value={candidate.id}>{candidate.registrationNumber}</option>
            ))}
          </SelectField>
        </div>
        {position === null ? (
          <Button onClick={locate} busy={locating}>Stations near me</Button>
        ) : (
          <Button variant="ghost" onClick={() => setPosition(null)}>Show all stations</Button>
        )}
        {position === null && stations.data && (
          <RegionSwitch value={region} onChange={setRegion} stations={stations.data.content} />
        )}
        {locationError && <p role="status" className="text-sm text-muted">{locationError}</p>}
      </div>

      <QueryState query={stations} rows={6}>
        {(page) => {
          const all = position === null ? page.content.filter((station) => inRegion(station, region)) : page.content
          const listed = position === null
            ? all.map((station) => ({ station, distanceKm: undefined as number | undefined }))
            : (nearby.data ?? []).map((entry) => ({ station: entry.station, distanceKm: entry.distanceKm }))
          return (
            <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
              <StationMap
                stations={all}
                height="clamp(18rem, 55vh, 34rem)"
                highlight={(station) => isCompatible(station, vehicle)}
                onSelect={(station) => navigate(`/driver/stations/${station.id}${vehicleParam ? `?vehicle=${vehicleParam}` : ''}`)}
              />
              <Panel
                title={position !== null ? `Within ${NEARBY_RADIUS_KM} km` : region === 'ALL' ? 'All stations' : REGIONS.find((r) => r.id === region)?.label}
                aside={`${listed.length} listed`}
                bodyClassName=""
              >
                {position !== null && nearby.isPending && <p className="p-5 text-sm text-muted">Finding stations near you…</p>}
                {position !== null && nearby.isError && <p role="alert" className="p-5 text-sm text-danger">{errorMessage(nearby.error)}</p>}
                {listed.length === 0 && !(position !== null && nearby.isPending) ? (
                  <EmptyState title="No stations in range">
                    The network covers the Mumbai region and Pune. Show all stations to browse it.
                  </EmptyState>
                ) : (
                  <ul className="lg:max-h-[34rem] lg:overflow-y-auto">
                    {listed.map(({ station, distanceKm }) => (
                      <StationRow
                        key={station.id}
                        station={station}
                        distanceKm={distanceKm}
                        compatible={isCompatible(station, vehicle)}
                        vehicleParam={vehicleParam}
                      />
                    ))}
                  </ul>
                )}
              </Panel>
            </div>
          )
        }}
      </QueryState>
      <p className="text-xs text-muted">
        Map markers: lime operational, amber maintenance, coral offline; larger markers have more connectors, and dimmed
        markers have no compatible connector. The station list carries the same information.
      </p>
    </div>
  )
}
