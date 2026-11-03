import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { SelectField, TextField } from '../../components/Field'
import { Button, FormError } from '../../components/ui'
import { makeBookingSchema, toBookingRequest, type BookingFormInput, type BookingFormValues } from '../../lib/bookingSchema'
import { ceilToSlot, localDateValue, localTimeValue, MAX_WINDOW_SLOTS, SLOT_MS, SLOT_TIMES, slotsLabel } from '../../lib/slots'
import { ApiError } from '../../services/api'
import type { BookingRequest, VehicleResponse } from '../../types/api'

/** Server field names mapped onto this form's controls. */
const SERVER_FIELDS: Record<string, keyof BookingFormInput> = {
  vehicleId: 'vehicleId',
  requestedStart: 'time',
  requestedEnd: 'slots',
  requestedEnergyKwh: 'energy',
}

export function BookingForm({ stationId, vehicles, now, onSubmit }: {
  stationId: number
  vehicles: VehicleResponse[]
  now?: Date
  onSubmit: (request: BookingRequest) => Promise<void>
}) {
  const [formError, setFormError] = useState<string | null>(null)
  // The form's clock is fixed when it mounts: validation and the default start use the same instant.
  const [{ clock, firstStart }] = useState(() => {
    const mountedAt = now ?? new Date()
    return { clock: mountedAt, firstStart: ceilToSlot(new Date(mountedAt.getTime() + SLOT_MS)) }
  })
  const schema = useMemo(() => makeBookingSchema(vehicles, clock), [vehicles, clock])
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<BookingFormInput, unknown, BookingFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      vehicleId: vehicles.length === 1 ? String(vehicles[0]!.id) : '',
      date: localDateValue(firstStart),
      time: localTimeValue(firstStart),
      slots: 4,
      energy: '',
    },
  })

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await onSubmit(toBookingRequest(values, stationId))
    } catch (error) {
      if (!(error instanceof ApiError)) {
        throw error
      }
      setFormError(error.message)
      for (const fieldError of error.fieldErrors) {
        const field = SERVER_FIELDS[fieldError.field]
        if (field) {
          setError(field, { type: 'server', message: fieldError.message })
        }
      }
    }
  })

  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-4">
      <FormError message={formError} />
      <SelectField label="Vehicle" error={errors.vehicleId?.message} {...register('vehicleId')}>
        <option value="">Choose a vehicle</option>
        {vehicles.map((vehicle) => (
          <option key={vehicle.id} value={vehicle.id}>{vehicle.registrationNumber}</option>
        ))}
      </SelectField>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <TextField label="Date" type="date" error={errors.date?.message} {...register('date')} />
        <SelectField label="Start time" error={errors.time?.message} {...register('time')}>
          {SLOT_TIMES.map((time) => <option key={time} value={time}>{time}</option>)}
        </SelectField>
        <SelectField label="Duration" error={errors.slots?.message} {...register('slots')}>
          {Array.from({ length: MAX_WINDOW_SLOTS }, (_, index) => index + 1).map((slots) => (
            <option key={slots} value={slots}>{slotsLabel(slots)}</option>
          ))}
        </SelectField>
      </div>
      <TextField
        label="Energy (kWh)"
        inputMode="decimal"
        hint="At most the battery headroom, and what the vehicle can take in the window."
        error={errors.energy?.message}
        {...register('energy')}
      />
      <div>
        <Button type="submit" variant="primary" busy={isSubmitting}>Request charging</Button>
      </div>
    </form>
  )
}
