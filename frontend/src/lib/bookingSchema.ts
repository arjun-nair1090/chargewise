import { z } from 'zod'
import type { BookingRequest, VehicleResponse } from '../types/api'
import { combineLocal, MAX_DAYS_AHEAD, MAX_WINDOW_SLOTS, SLOT_MINUTES, SLOT_MS, toIsoSeconds } from './slots'

/**
 * Client-side mirror of the server's booking rules 1–6 (docs/api-documentation.md, "Bookings"). The server stays
 * the authority: rule 7 (overlap) and any rule this mirror misses come back as field errors and are shown inline.
 */
export function makeBookingSchema(vehicles: VehicleResponse[], now: Date) {
  return z
    .object({
      vehicleId: z.string().min(1, 'Choose a vehicle.'),
      date: z.string().min(1, 'Choose a date.'),
      time: z.string().regex(/^\d{2}:(00|15|30|45)$/, 'Start on a 15-minute boundary (:00, :15, :30 or :45).'),
      slots: z.coerce
        .number()
        .int()
        .min(1, 'The window must be at least 15 minutes.')
        .max(MAX_WINDOW_SLOTS, 'The window can be at most 12 hours.'),
      energy: z
        .string()
        .trim()
        .min(1, 'Enter the energy to charge.')
        .regex(/^\d+(\.\d{1,2})?$/, 'Use a positive number with at most two decimals.')
        .refine((value) => Number(value) > 0, 'Energy must be more than 0 kWh.'),
    })
    .superRefine((values, ctx) => {
      const start = combineLocal(values.date, values.time)
      if (start === null) {
        ctx.addIssue({ code: 'custom', path: ['date'], message: 'Enter a valid date.' })
        return
      }
      if (start.getTime() <= now.getTime()) {
        ctx.addIssue({ code: 'custom', path: ['time'], message: 'The window must start in the future.' })
      } else if (start.getTime() > now.getTime() + MAX_DAYS_AHEAD * 86_400_000) {
        ctx.addIssue({ code: 'custom', path: ['date'], message: 'The window must start within the next 14 days.' })
      }

      const vehicle = vehicles.find((candidate) => String(candidate.id) === values.vehicleId)
      const energy = Number(values.energy)
      if (!vehicle || !(energy > 0)) {
        return
      }
      const headroom = (vehicle.batteryCapacityKwh * (100 - vehicle.currentSoc)) / 100
      if (energy > headroom) {
        ctx.addIssue({
          code: 'custom',
          path: ['energy'],
          message: `That is more than the ${round2(headroom)} kWh this battery can take at ${vehicle.currentSoc}% charge.`,
        })
        return
      }
      const deliverable = (vehicle.maxChargingKw * values.slots * SLOT_MINUTES) / 60
      if (energy > deliverable) {
        ctx.addIssue({
          code: 'custom',
          path: ['energy'],
          message: `At ${vehicle.maxChargingKw} kW this vehicle can take at most ${round2(deliverable)} kWh in this window.`,
        })
      }
    })
}

export type BookingFormInput = z.input<ReturnType<typeof makeBookingSchema>>
export type BookingFormValues = z.output<ReturnType<typeof makeBookingSchema>>

export function toBookingRequest(values: BookingFormValues, stationId: number): BookingRequest {
  const start = combineLocal(values.date, values.time)!
  return {
    vehicleId: Number(values.vehicleId),
    stationId,
    requestedEnergyKwh: Number(values.energy),
    requestedStart: toIsoSeconds(start),
    requestedEnd: toIsoSeconds(new Date(start.getTime() + values.slots * SLOT_MS)),
  }
}

function round2(value: number): number {
  return Math.round(value * 100) / 100
}
