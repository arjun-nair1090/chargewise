import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/api'
import type { BookingRequest, VehicleResponse } from '../../types/api'
import { BookingForm } from './BookingForm'

const NOW = new Date(2026, 10, 2, 10, 7)

const vehicle: VehicleResponse = {
  id: 7,
  registrationNumber: 'MH01AB1234',
  batteryCapacityKwh: 60,
  currentSoc: 50,
  maxChargingKw: 7,
  connectorTypes: ['TYPE2_AC'],
  createdAt: '2026-11-01T09:00:00Z',
}

function setup(onSubmit = vi.fn<(request: BookingRequest) => Promise<void>>(async () => {})) {
  render(<BookingForm stationId={3} vehicles={[vehicle]} now={NOW} onSubmit={onSubmit} />)
  return { user: userEvent.setup(), onSubmit }
}

async function fill(user: ReturnType<typeof userEvent.setup>, values: { date?: string; time?: string; slots?: string; energy?: string }) {
  if (values.date !== undefined) {
    const date = screen.getByLabelText('Date')
    await user.clear(date)
    await user.type(date, values.date)
  }
  if (values.time !== undefined) {
    await user.selectOptions(screen.getByLabelText('Start time'), values.time)
  }
  if (values.slots !== undefined) {
    await user.selectOptions(screen.getByLabelText('Duration'), values.slots)
  }
  if (values.energy !== undefined) {
    const energy = screen.getByLabelText('Energy (kWh)')
    await user.clear(energy)
    await user.type(energy, values.energy)
  }
  await user.click(screen.getByRole('button', { name: 'Request charging' }))
}

describe('BookingForm', () => {
  it('rejects energy above the battery headroom', async () => {
    const { user, onSubmit } = setup()

    await fill(user, { date: '2026-11-03', time: '10:00', slots: '24', energy: '35' })

    expect(await screen.findByText(/more than the 30 kWh this battery can take/)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects energy above charge rate times window length', async () => {
    const { user, onSubmit } = setup()

    await fill(user, { date: '2026-11-03', time: '10:00', slots: '4', energy: '10' })

    expect(await screen.findByText(/at most 7 kWh in this window/)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects a start in the past', async () => {
    const { user, onSubmit } = setup()

    await fill(user, { date: '2026-11-02', time: '09:00', energy: '5' })

    expect(await screen.findByText('The window must start in the future.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects a start more than 14 days ahead', async () => {
    const { user, onSubmit } = setup()

    await fill(user, { date: '2026-11-20', time: '10:00', energy: '5' })

    expect(await screen.findByText('The window must start within the next 14 days.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits offset timestamps on slot boundaries', async () => {
    const { user, onSubmit } = setup()

    await fill(user, { date: '2026-11-03', time: '10:15', slots: '4', energy: '6.5' })

    expect(onSubmit).toHaveBeenCalledWith({
      vehicleId: 7,
      stationId: 3,
      requestedEnergyKwh: 6.5,
      requestedStart: new Date(2026, 10, 3, 10, 15).toISOString().replace('.000Z', 'Z'),
      requestedEnd: new Date(2026, 10, 3, 11, 15).toISOString().replace('.000Z', 'Z'),
    })
  })

  it('shows server field errors inline', async () => {
    const onSubmit = vi.fn(async () => {
      throw new ApiError(409, 'CONFLICT', 'Vehicle already has an overlapping booking', [
        { field: 'requestedStart', message: 'overlaps booking 41 for this vehicle' },
      ])
    })
    const { user } = setup(onSubmit)

    await fill(user, { date: '2026-11-03', time: '10:00', slots: '4', energy: '5' })

    expect(await screen.findByText('overlaps booking 41 for this vehicle')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Vehicle already has an overlapping booking')
  })
})
