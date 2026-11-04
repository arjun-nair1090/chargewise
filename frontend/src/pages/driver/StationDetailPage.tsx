import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { SelectField } from '../../components/Field'
import { QueryState } from '../../components/QueryState'
import { SlotBarChart, type SlotBar } from '../../components/SlotBarChart'
import { StatusChip } from '../../components/StatusChip'
import { buttonClass } from '../../components/buttonClass'
import { EmptyState, Mono, PageHeader, Panel } from '../../components/ui'
import { connectorLabel, formatKw, formatTime } from '../../lib/format'
import { floorToSlot, localDateValue, MAX_DAYS_AHEAD, SLOT_MS, toIsoSeconds } from '../../lib/slots'
import { bookingApi, stationApi, vehicleApi } from '../../services/endpoints'
import type { AvailabilityResponse, ConnectorType, VehicleResponse } from '../../types/api'
import { BookingForm } from './BookingForm'

const DAY_SLOTS = 96

/** The next 15 days as availability windows: today from the current slot, later days from local midnight. */
function dayOptions(now: Date) {
  return Array.from({ length: MAX_DAYS_AHEAD + 1 }, (_, offset) => {
    const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + offset)
    const from = offset === 0 ? floorToSlot(now) : midnight
    return {
      value: localDateValue(midnight),
      label: offset === 0 ? 'Today' : midnight.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' }),
      from,
    }
  })
}

function availabilityBars(availability: AvailabilityResponse, vehicle: VehicleResponse | undefined): SlotBar[] {
  return availability.slots.map((slot) => {
    const byType = Object.entries(slot.freeByType)
    const value = vehicle
      ? byType.filter(([type]) => vehicle.connectorTypes.includes(type as ConnectorType)).reduce((sum, [, free]) => sum + (free ?? 0), 0)
      : slot.freeConnectors
    return {
      label: formatTime(slot.start),
      value,
      detail: byType.map(([type, free]) => `${connectorLabel(type)}: ${free} free`),
    }
  })
}

export function StationDetailPage() {
  const stationId = Number(useParams().stationId)
  const [params, setParams] = useSearchParams()
  const vehicleParam = params.get('vehicle') ?? ''
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [days] = useState(() => dayOptions(new Date()))
  const [day, setDay] = useState(days[0]!.value)
  const selected = days.find((option) => option.value === day) ?? days[0]!
  const from = toIsoSeconds(selected.from)
  const to = toIsoSeconds(new Date(selected.from.getTime() + DAY_SLOTS * SLOT_MS))

  const station = useQuery({ queryKey: ['station', stationId], queryFn: () => stationApi.get(stationId) })
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: vehicleApi.list })
  const availability = useQuery({
    queryKey: ['availability', stationId, from, to],
    queryFn: () => stationApi.availability(stationId, from, to),
  })
  const vehicle = vehicles.data?.find((candidate) => String(candidate.id) === vehicleParam)

  return (
    <QueryState query={station} rows={8}>
      {(data) => (
        <div className="flex flex-col gap-6">
          <div>
            <Link to="/driver/stations" className="text-sm text-muted underline hover:text-text">All stations</Link>
          </div>
          <PageHeader
            title={data.name}
            job={data.address}
            badges={<StatusChip status={data.status} />}
          />

          <div className="grid gap-6 xl:grid-cols-[minmax(0,3fr)_minmax(22rem,2fr)]">
            <div className="flex min-w-0 flex-col gap-6">
              <div className="w-full sm:w-64">
                <SelectField
                  label="Check compatibility for"
                  value={vehicleParam}
                  onChange={(event) => setParams(event.target.value ? { vehicle: event.target.value } : {}, { replace: true })}
                >
                  <option value="">Any vehicle</option>
                  {vehicles.data?.map((candidate) => (
                    <option key={candidate.id} value={candidate.id}>{candidate.registrationNumber}</option>
                  ))}
                </SelectField>
              </div>

              <Panel title="Connectors" aside={`Grid import limit ${formatKw(data.gridCapacityKw)}`} bodyClassName="overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Connector</th>
                      <th scope="col">Status</th>
                      {vehicle && <th scope="col">{vehicle.registrationNumber}</th>}
                      <th scope="col">Type</th>
                      <th scope="col" className="text-right">Rated power</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.connectors.map((connector) => (
                      <tr key={connector.id}>
                        <td><Mono>#{connector.id}</Mono></td>
                        <td><StatusChip status={connector.status} /></td>
                        {vehicle && (
                          <td className={`whitespace-nowrap ${vehicle.connectorTypes.includes(connector.connectorType) ? '' : 'text-muted'}`}>
                            {vehicle.connectorTypes.includes(connector.connectorType) ? 'Compatible' : 'Not compatible'}
                          </td>
                        )}
                        <td className="whitespace-nowrap">{connectorLabel(connector.connectorType)}</td>
                        <td className="text-right">{formatKw(connector.maxPowerKw)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Panel>

              <Panel
                title={vehicle ? `Free compatible connectors per 15-minute slot` : 'Free connectors per 15-minute slot'}
                aside={
                  <label className="inline-flex items-center gap-2">
                    <span className="sr-only">Day</span>
                    <select
                      value={day}
                      onChange={(event) => setDay(event.target.value)}
                      className="h-8 rounded-md border border-border bg-bg px-2 text-sm text-text"
                    >
                      {days.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                    </select>
                  </label>
                }
              >
                <QueryState query={availability} rows={5}>
                  {(slots) => (
                    <>
                      <p className="mb-3 text-sm text-muted">
                        {slots.operationalConnectors} operational connectors. A connector is free in a slot when no assigned
                        booking holds it.
                      </p>
                      <SlotBarChart
                        bars={availabilityBars(slots, vehicle)}
                        unit="free"
                        title={`Free connectors per slot at ${data.name}`}
                        maxValue={Math.max(1, slots.operationalConnectors)}
                      />
                    </>
                  )}
                </QueryState>
              </Panel>
            </div>

            <Panel title="Request a charging window" className="self-start">
              <QueryState query={vehicles} rows={5}>
                {(list) => list.length === 0 ? (
                  <EmptyState title="Add a vehicle first">
                    <p>A request needs a vehicle so the matching can check connector compatibility.</p>
                    <Link to="/driver/garage" className={`${buttonClass('secondary', 'sm')} mt-4`}>Go to garage</Link>
                  </EmptyState>
                ) : (
                  <>
                    <p className="mb-4 text-sm text-muted">
                      Your request stays pending until the operator runs allocation. Matching then assigns a connector,
                      and Edmonds-Karp sets your power within the station&rsquo;s grid limit.
                    </p>
                    <BookingForm
                      stationId={data.id}
                      vehicles={list}
                      onSubmit={async (request) => {
                        const booking = await bookingApi.create(request)
                        await queryClient.invalidateQueries({ queryKey: ['bookings'] })
                        navigate(`/driver/bookings/${booking.id}`)
                      }}
                    />
                  </>
                )}
              </QueryState>
            </Panel>
          </div>
        </div>
      )}
    </QueryState>
  )
}
