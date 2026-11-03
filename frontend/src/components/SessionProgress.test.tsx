import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { SessionResponse } from '../types/api'
import { SessionProgress } from './SessionProgress'

const session: SessionResponse = {
  id: 12,
  bookingId: 40,
  stationId: 3,
  stationName: 'Baner Hub',
  connectorId: 18,
  connectorType: 'CCS2',
  vehicleRegistration: 'MH01AB1234',
  status: 'ACTIVE',
  windowStart: '2026-11-02T10:00:00Z',
  windowEnd: '2026-11-02T11:00:00Z',
  startTime: '2026-11-02T10:00:00Z',
  endTime: null,
  requestedEnergyKwh: 20,
  energyDeliveredKwh: 12.4,
  allocatedPowerKw: 24.8,
}

describe('SessionProgress', () => {
  it('labels the session as simulated', () => {
    render(<SessionProgress session={session} />)

    expect(screen.getByText('Simulated')).toBeInTheDocument()
    expect(screen.getByText(/constant-power model/)).toBeInTheDocument()
  })

  it('shows delivered energy over requested energy', () => {
    render(<SessionProgress session={session} />)

    expect(screen.getByText(/12\.4 of 20 kWh/)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '62')
  })
})
