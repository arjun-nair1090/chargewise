import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { TextField } from '../../components/Field'
import { QueryState } from '../../components/QueryState'
import { Button, EmptyState, FormError, Mono, PageHeader, Panel } from '../../components/ui'
import { errorMessage } from '../../lib/errors'
import { connectorLabel, formatKw, formatKwh, formatNumber } from '../../lib/format'
import { ApiError } from '../../services/api'
import { vehicleApi } from '../../services/endpoints'
import { CONNECTOR_TYPES, type VehicleRequest, type VehicleResponse } from '../../types/api'

const twoDecimals = /^\d+(\.\d{1,2})?$/

/** Mirrors the server's VehicleRequest validation (docs/api-documentation.md, "Vehicles"). */
const vehicleSchema = z.object({
  registrationNumber: z
    .string()
    .trim()
    .min(1, 'Enter the registration number.')
    .max(40, 'Use at most 40 characters.')
    .regex(/^[A-Za-z0-9 -]+$/, 'Use letters, digits, spaces and hyphens only.'),
  batteryCapacityKwh: z
    .string()
    .trim()
    .regex(twoDecimals, 'Use a positive number with at most two decimals.')
    .refine((value) => Number(value) > 0, 'Battery capacity must be more than 0 kWh.'),
  currentSoc: z
    .string()
    .trim()
    .regex(twoDecimals, 'Use a number from 0 to 100 with at most two decimals.')
    .refine((value) => Number(value) <= 100, 'State of charge is at most 100%.'),
  maxChargingKw: z
    .string()
    .trim()
    .regex(twoDecimals, 'Use a positive number with at most two decimals.')
    .refine((value) => Number(value) > 0, 'Maximum charging power must be more than 0 kW.'),
  connectorTypes: z.array(z.enum(CONNECTOR_TYPES)).min(1, 'Choose at least one connector type.'),
})
type VehicleFormValues = z.infer<typeof vehicleSchema>

function toFormValues(vehicle?: VehicleResponse): VehicleFormValues {
  return {
    registrationNumber: vehicle?.registrationNumber ?? '',
    batteryCapacityKwh: vehicle ? String(vehicle.batteryCapacityKwh) : '',
    currentSoc: vehicle ? String(vehicle.currentSoc) : '',
    maxChargingKw: vehicle ? String(vehicle.maxChargingKw) : '',
    connectorTypes: vehicle?.connectorTypes ?? [],
  }
}

function VehicleForm({ vehicle, onDone }: { vehicle?: VehicleResponse; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<VehicleFormValues>({
    resolver: zodResolver(vehicleSchema),
    defaultValues: toFormValues(vehicle),
  })

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    const body: VehicleRequest = {
      registrationNumber: values.registrationNumber,
      batteryCapacityKwh: Number(values.batteryCapacityKwh),
      currentSoc: Number(values.currentSoc),
      maxChargingKw: Number(values.maxChargingKw),
      connectorTypes: values.connectorTypes,
    }
    try {
      if (vehicle) {
        await vehicleApi.update(vehicle.id, body)
      } else {
        await vehicleApi.create(body)
      }
      await queryClient.invalidateQueries({ queryKey: ['vehicles'] })
      onDone()
    } catch (error) {
      setFormError(errorMessage(error))
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field in body) {
            setError(fieldError.field as keyof VehicleFormValues, { type: 'server', message: fieldError.message })
          }
        }
      }
    }
  })

  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-4">
      <FormError message={formError} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Registration number"
          hint="Stored upper case without spaces or hyphens."
          error={errors.registrationNumber?.message}
          {...register('registrationNumber')}
        />
        <TextField label="Battery capacity (kWh)" inputMode="decimal" error={errors.batteryCapacityKwh?.message} {...register('batteryCapacityKwh')} />
        <TextField label="Current state of charge (%)" inputMode="decimal" error={errors.currentSoc?.message} {...register('currentSoc')} />
        <TextField label="Maximum charging power (kW)" inputMode="decimal" error={errors.maxChargingKw?.message} {...register('maxChargingKw')} />
      </div>
      <fieldset aria-describedby={errors.connectorTypes ? 'connector-types-error' : undefined}>
        <legend className="text-sm font-medium">Connector types</legend>
        <div className="mt-2 flex flex-wrap gap-x-5 gap-y-2">
          {CONNECTOR_TYPES.map((type) => (
            <label key={type} className="inline-flex items-center gap-2 text-sm">
              <input type="checkbox" value={type} className="size-4 accent-[var(--color-accent)]" {...register('connectorTypes')} />
              {connectorLabel(type)}
            </label>
          ))}
        </div>
        {errors.connectorTypes && <p id="connector-types-error" className="mt-1.5 text-xs text-danger">{errors.connectorTypes.message}</p>}
      </fieldset>
      <div className="flex gap-3">
        <Button type="submit" variant="primary" busy={isSubmitting}>{vehicle ? 'Save changes' : 'Add vehicle'}</Button>
        <Button variant="ghost" onClick={onDone}>Cancel</Button>
      </div>
    </form>
  )
}

export function GaragePage() {
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: vehicleApi.list })
  const [editing, setEditing] = useState<VehicleResponse | 'new' | null>(null)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Garage"
        job="Your vehicles decide which connectors you are compatible with and how much energy a request may ask for."
        actions={editing === null && <Button variant="primary" onClick={() => setEditing('new')}>Add vehicle</Button>}
      />
      {editing !== null && (
        <Panel title={editing === 'new' ? 'Add a vehicle' : `Edit ${editing.registrationNumber}`}>
          <VehicleForm key={editing === 'new' ? 'new' : editing.id} vehicle={editing === 'new' ? undefined : editing} onDone={() => setEditing(null)} />
        </Panel>
      )}
      <Panel title="Vehicles" bodyClassName="">
        <QueryState
          query={vehicles}
          isEmpty={(data) => data.length === 0}
          empty={
            <EmptyState title="No vehicles yet">
              Add a vehicle with its connector types, battery size and state of charge before requesting a charging window.
            </EmptyState>
          }
        >
          {(data) => (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Registration</th>
                    <th scope="col">Connectors</th>
                    <th scope="col" className="text-right">Battery</th>
                    <th scope="col" className="text-right">Charge</th>
                    <th scope="col" className="text-right">Headroom</th>
                    <th scope="col" className="text-right">Max power</th>
                    <th scope="col"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((vehicle) => (
                    <tr key={vehicle.id}>
                      <td><Mono>{vehicle.registrationNumber}</Mono></td>
                      <td>{vehicle.connectorTypes.map(connectorLabel).join(', ')}</td>
                      <td className="text-right">{formatKwh(vehicle.batteryCapacityKwh)}</td>
                      <td className="text-right">{formatNumber(vehicle.currentSoc)}%</td>
                      <td className="text-right">{formatKwh((vehicle.batteryCapacityKwh * (100 - vehicle.currentSoc)) / 100)}</td>
                      <td className="text-right">{formatKw(vehicle.maxChargingKw)}</td>
                      <td className="text-right">
                        <Button size="sm" variant="ghost" onClick={() => setEditing(vehicle)}>
                          Edit<span className="sr-only"> {vehicle.registrationNumber}</span>
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryState>
      </Panel>
    </div>
  )
}
