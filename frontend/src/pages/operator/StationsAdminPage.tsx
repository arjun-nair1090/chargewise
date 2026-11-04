import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { SelectField, TextField } from '../../components/Field'
import { PlaceFilter } from '../../components/PlaceFilter'
import { QueryState } from '../../components/QueryState'
import { StatusChip } from '../../components/StatusChip'
import { Button, FormError, Mono, PageHeader, Panel } from '../../components/ui'
import { errorMessage } from '../../lib/errors'
import { connectorLabel, formatKw, formatNumber, humanise } from '../../lib/format'
import { ALL_INDIA, INDIAN_STATES, inPlace, type Place } from '../../lib/places'
import { ApiError } from '../../services/api'
import { operatorApi } from '../../services/endpoints'
import {
  CONNECTOR_STATUSES,
  CONNECTOR_TYPES,
  STATION_STATUSES,
  type StationRequest,
  type StationResponse,
} from '../../types/api'
import { useStations } from './useStationNames'

const LIST_PAGE = 60

const decimal = (max: number, message: string) =>
  z.string().trim().regex(/^-?\d+(\.\d+)?$/, message).refine((value) => Math.abs(Number(value)) <= max, message)

/** Mirrors StationRequest validation on the server. */
const stationSchema = z.object({
  name: z.string().trim().min(1, 'Enter a name.').max(160, 'Use at most 160 characters.'),
  address: z.string().trim().min(1, 'Enter an address.').max(500, 'Use at most 500 characters.'),
  city: z.string().trim().max(80, 'Use at most 80 characters.'),
  state: z.string(),
  latitude: decimal(90, 'Latitude is a number from -90 to 90.'),
  longitude: decimal(180, 'Longitude is a number from -180 to 180.'),
  gridCapacityKw: z
    .string()
    .trim()
    .regex(/^\d+(\.\d{1,2})?$/, 'Use a number of kW with at most two decimals.'),
  status: z.enum(STATION_STATUSES),
})
type StationFormValues = z.infer<typeof stationSchema>

const connectorSchema = z.object({
  connectorType: z.enum(CONNECTOR_TYPES),
  maxPowerKw: z
    .string()
    .trim()
    .regex(/^\d+(\.\d{1,2})?$/, 'Use a number of kW with at most two decimals.')
    .refine((value) => Number(value) > 0, 'Rated power must be more than 0 kW.'),
  status: z.enum(CONNECTOR_STATUSES),
})
type ConnectorFormValues = z.infer<typeof connectorSchema>

function useInvalidateNetwork() {
  const queryClient = useQueryClient()
  return async () => {
    await queryClient.invalidateQueries({ queryKey: ['stations'] })
    await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    await queryClient.invalidateQueries({ queryKey: ['operator'] })
  }
}

function StationForm({ station, onSaved }: { station?: StationResponse; onSaved: (station: StationResponse) => void }) {
  const invalidate = useInvalidateNetwork()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<StationFormValues>({
    resolver: zodResolver(stationSchema),
    defaultValues: {
      name: station?.name ?? '',
      address: station?.address ?? '',
      city: station?.city ?? '',
      state: station?.state ?? '',
      latitude: station ? String(station.latitude) : '',
      longitude: station ? String(station.longitude) : '',
      gridCapacityKw: station ? String(station.gridCapacityKw) : '',
      status: station?.status ?? 'OPERATIONAL',
    },
  })

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    const body: StationRequest = {
      ...values,
      city: values.city || null,
      state: values.state || null,
      latitude: Number(values.latitude),
      longitude: Number(values.longitude),
      gridCapacityKw: Number(values.gridCapacityKw),
    }
    try {
      const saved = station ? await operatorApi.updateStation(station.id, body) : await operatorApi.createStation(body)
      await invalidate()
      onSaved(saved)
    } catch (error) {
      setFormError(errorMessage(error))
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field in body) {
            setError(fieldError.field as keyof StationFormValues, { type: 'server', message: fieldError.message })
          }
        }
      }
    }
  })

  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-4">
      <FormError message={formError} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Name" error={errors.name?.message} {...register('name')} />
        <SelectField label="Status" error={errors.status?.message} {...register('status')}>
          {STATION_STATUSES.map((status) => <option key={status} value={status}>{humanise(status)}</option>)}
        </SelectField>
      </div>
      <TextField label="Address" error={errors.address?.message} {...register('address')} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="City" error={errors.city?.message} {...register('city')} />
        <SelectField label="State or union territory" error={errors.state?.message} {...register('state')}>
          <option value="">Not set</option>
          {INDIAN_STATES.map((state) => <option key={state} value={state}>{state}</option>)}
        </SelectField>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField label="Latitude" inputMode="decimal" error={errors.latitude?.message} {...register('latitude')} />
        <TextField label="Longitude" inputMode="decimal" error={errors.longitude?.message} {...register('longitude')} />
        <TextField label="Grid import limit (kW)" inputMode="decimal" error={errors.gridCapacityKw?.message} {...register('gridCapacityKw')} />
      </div>
      {station && (
        <p className="text-sm text-muted">
          Saving applies the outage policy at once: taking the station offline or into maintenance waitlists its future
          assignments and interrupts sessions under way; lowering the grid limit re-runs Edmonds-Karp for its live sessions.
        </p>
      )}
      <div>
        <Button type="submit" variant="primary" busy={isSubmitting}>{station ? 'Save station' : 'Create station'}</Button>
      </div>
    </form>
  )
}

function ConnectorRows({ station }: { station: StationResponse }) {
  const invalidate = useInvalidateNetwork()
  const toggle = useMutation({
    mutationFn: ({ id, out }: { id: number; out: boolean }) =>
      operatorApi.setConnectorStatus(id, out ? 'OUT_OF_SERVICE' : 'OPERATIONAL'),
    onSuccess: invalidate,
  })
  return (
    <>
      {toggle.isError && <div className="px-5 pt-3"><FormError message={errorMessage(toggle.error)} /></div>}
      <table className="data-table">
        <thead>
          <tr>
            <th scope="col">Connector</th>
            <th scope="col">Type</th>
            <th scope="col" className="text-right">Rated power</th>
            <th scope="col">Status</th>
            <th scope="col"><span className="sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          {station.connectors.map((connector) => {
            const operational = connector.status === 'OPERATIONAL'
            return (
              <tr key={connector.id}>
                <td><Mono>#{connector.id}</Mono></td>
                <td>{connectorLabel(connector.connectorType)}</td>
                <td className="text-right">{formatKw(connector.maxPowerKw)}</td>
                <td><StatusChip status={connector.status} /></td>
                <td className="text-right">
                  <Button
                    size="sm"
                    variant={operational ? 'danger' : 'secondary'}
                    busy={toggle.isPending && toggle.variables?.id === connector.id}
                    onClick={() => toggle.mutate({ id: connector.id, out: operational })}
                  >
                    {operational ? 'Take out of service' : 'Return to service'}
                    <span className="sr-only"> connector {connector.id}</span>
                  </Button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </>
  )
}

function AddConnectorForm({ station }: { station: StationResponse }) {
  const invalidate = useInvalidateNetwork()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<ConnectorFormValues>({
    resolver: zodResolver(connectorSchema),
    defaultValues: { connectorType: 'CCS2', maxPowerKw: '', status: 'OPERATIONAL' },
  })
  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await operatorApi.addConnector(station.id, { ...values, maxPowerKw: Number(values.maxPowerKw) })
      await invalidate()
      reset()
    } catch (error) {
      setFormError(errorMessage(error))
    }
  })
  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-4">
      <FormError message={formError} />
      <div className="grid gap-4 sm:grid-cols-[1fr_1fr_1fr_auto] sm:items-end">
        <SelectField label="Type" {...register('connectorType')}>
          {CONNECTOR_TYPES.map((type) => <option key={type} value={type}>{connectorLabel(type)}</option>)}
        </SelectField>
        <TextField label="Rated power (kW)" inputMode="decimal" error={errors.maxPowerKw?.message} {...register('maxPowerKw')} />
        <SelectField label="Status" {...register('status')}>
          {CONNECTOR_STATUSES.map((status) => <option key={status} value={status}>{humanise(status)}</option>)}
        </SelectField>
        <Button type="submit" busy={isSubmitting}>Add connector</Button>
      </div>
    </form>
  )
}

function DemoReset() {
  const invalidate = useInvalidateNetwork()
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const reset = useMutation({
    mutationFn: operatorApi.resetDemo,
    onSuccess: async () => {
      setConfirming(false)
      await queryClient.invalidateQueries()
      await invalidate()
    },
  })
  return (
    <div className="flex flex-col items-end gap-2">
      {confirming ? (
        <div className="flex gap-2">
          <Button variant="danger" busy={reset.isPending} onClick={() => reset.mutate()}>Reload demo dataset</Button>
          <Button variant="ghost" onClick={() => setConfirming(false)}>Keep current data</Button>
        </div>
      ) : (
        <Button onClick={() => setConfirming(true)}>Reset demo data</Button>
      )}
      {reset.isSuccess && <p role="status" className="text-sm text-muted">Reloaded {reset.data.stations} stations and {reset.data.bookings} requests.</p>}
      {reset.isError && <FormError message={errorMessage(reset.error)} />}
    </div>
  )
}

export function StationsAdminPage() {
  const stations = useStations()
  const [selected, setSelected] = useState<number | 'new' | null>(null)
  const [search, setSearch] = useState('')
  const [place, setPlace] = useState<Place>(ALL_INDIA)
  const [shown, setShown] = useState(LIST_PAGE)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Stations"
        job="Change station status, grid limits and connectors; outages and grid cuts apply the outage policy at once."
        actions={<><Button onClick={() => setSelected('new')}>New station</Button><DemoReset /></>}
      />
      <QueryState query={stations} rows={8}>
        {(network) => {
          const current = typeof selected === 'number' ? network.find((station) => station.id === selected) : undefined
          const needle = search.trim().toLowerCase()
          const matches = network.filter((station) => inPlace(station, place)
            && (!needle || `${station.name} ${station.address}`.toLowerCase().includes(needle)))
          const visible = matches.slice(0, shown)
          return (
            <div className="flex flex-col gap-6">
            <div className="flex flex-wrap items-end gap-3">
              <div className="w-full sm:w-72">
                <TextField
                  label="Search"
                  type="search"
                  placeholder="Name or address"
                  value={search}
                  onChange={(event) => {
                    setSearch(event.target.value)
                    setShown(LIST_PAGE)
                  }}
                />
              </div>
              <PlaceFilter
                value={place}
                onChange={(next) => {
                  setPlace(next)
                  setShown(LIST_PAGE)
                }}
                stations={network}
              />
            </div>
            <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
              <Panel
                title="Network"
                aside={matches.length === network.length
                  ? `${formatNumber(network.length)} stations`
                  : `${formatNumber(matches.length)} of ${formatNumber(network.length)}`}
                bodyClassName="p-2"
              >
                {matches.length === 0 && <p className="px-3 py-2.5 text-sm text-muted">No station matches this search.</p>}
                <ul className="flex flex-col lg:max-h-[48rem] lg:overflow-y-auto">
                  {visible.map((station) => (
                    <li key={station.id}>
                      <button
                        type="button"
                        aria-pressed={selected === station.id}
                        onClick={() => setSelected(station.id)}
                        className={`flex w-full flex-col gap-1 rounded-md px-3 py-2.5 text-left transition-colors duration-150 ${
                          selected === station.id ? 'bg-surface-raised' : 'hover:bg-surface-raised/60'
                        }`}
                      >
                        <span className="font-medium">{station.name}</span>
                        <span className="text-xs text-muted">{[station.city, station.state].filter(Boolean).join(', ') || 'Place not set'}</span>
                        <span className="flex flex-wrap gap-x-4 text-sm text-muted">
                          <StatusChip status={station.status} />
                          <span>{formatKw(station.gridCapacityKw)} grid</span>
                          <span>{station.connectors.length} {station.connectors.length === 1 ? 'connector' : 'connectors'}</span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
                {matches.length > visible.length && (
                  <div className="flex items-center justify-between gap-3 border-t border-border px-3 pt-2.5 pb-1">
                    <span className="text-sm text-muted">Showing {formatNumber(visible.length)} of {formatNumber(matches.length)}</span>
                    <Button size="sm" onClick={() => setShown(shown + LIST_PAGE)}>
                      Show {Math.min(LIST_PAGE, matches.length - visible.length)} more
                    </Button>
                  </div>
                )}
              </Panel>
              <div className="flex min-w-0 flex-col gap-6">
                {selected === 'new' && (
                  <Panel title="New station">
                    <StationForm onSaved={(station) => setSelected(station.id)} />
                  </Panel>
                )}
                {current && (
                  <>
                    <Panel title={current.name}>
                      <StationForm key={`${current.id}-${current.status}-${current.gridCapacityKw}`} station={current} onSaved={() => undefined} />
                    </Panel>
                    <Panel title="Connectors" bodyClassName="overflow-x-auto">
                      <ConnectorRows station={current} />
                    </Panel>
                    <Panel title="Add a connector">
                      <AddConnectorForm station={current} />
                    </Panel>
                  </>
                )}
                {selected === null && (
                  <Panel>
                    <p className="text-sm text-muted">Select a station to edit it, or create a new one.</p>
                  </Panel>
                )}
              </div>
            </div>
            </div>
          )
        }}
      </QueryState>
    </div>
  )
}
