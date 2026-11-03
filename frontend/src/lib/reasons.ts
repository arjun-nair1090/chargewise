import type { StatusReason } from '../types/api'

export type ReasonText = {
  /** Short label for tables and chips. */
  label: string
  /** One plain-language sentence a driver or examiner can read aloud. */
  sentence: string
  /** REJECTED reasons are permanent for the request as submitted; WAITLISTED ones are retried by the next run. */
  outcome: 'REJECTED' | 'WAITLISTED' | 'UNKNOWN'
}

const RETRY = 'The next allocation run will try again.'

const REASONS: Record<StatusReason, ReasonText> = {
  NO_COMPATIBLE_CONNECTOR_TYPE: {
    label: 'No compatible connector',
    sentence: 'No connector at this station matches any of the vehicle’s connector types.',
    outcome: 'REJECTED',
  },
  INSUFFICIENT_CONNECTOR_POWER: {
    label: 'Connectors too slow',
    sentence: 'No compatible connector here can deliver the requested energy within the window.',
    outcome: 'REJECTED',
  },
  WINDOW_EXPIRED: {
    label: 'Window expired',
    sentence: 'The requested window started before the request could be assigned.',
    outcome: 'REJECTED',
  },
  ENERGY_EXCEEDS_BATTERY_HEADROOM: {
    label: 'Exceeds battery headroom',
    sentence: 'The requested energy is more than the battery can take at its current state of charge.',
    outcome: 'REJECTED',
  },
  STATION_NOT_OPERATIONAL: {
    label: 'Station not operational',
    sentence: `The station is offline or under maintenance. ${RETRY}`,
    outcome: 'WAITLISTED',
  },
  CONNECTORS_OUT_OF_SERVICE: {
    label: 'Connectors out of service',
    sentence: `Every compatible connector at this station is out of service. ${RETRY}`,
    outcome: 'WAITLISTED',
  },
  CONNECTORS_BOOKED_IN_WINDOW: {
    label: 'Connectors booked',
    sentence: `Every compatible connector is already booked for part of this window. ${RETRY}`,
    outcome: 'WAITLISTED',
  },
  LOST_CONTENTION: {
    label: 'Lost contention',
    sentence: `Compatible connectors were free, but the maximum matching gave them to other requests. ${RETRY}`,
    outcome: 'WAITLISTED',
  },
  INSUFFICIENT_GRID_CAPACITY: {
    label: 'Grid capacity reached',
    sentence: `The station’s grid-import limit left no power for this session. ${RETRY}`,
    outcome: 'WAITLISTED',
  },
  STATION_OUTAGE: {
    label: 'Station outage',
    sentence: `The station went out of service after this request was assigned. ${RETRY}`,
    outcome: 'WAITLISTED',
  },
  CONNECTOR_OUTAGE: {
    label: 'Connector outage',
    sentence: `The assigned connector went out of service. ${RETRY}`,
    outcome: 'WAITLISTED',
  },
}

export function describeReason(reason: string): ReasonText {
  if (Object.hasOwn(REASONS, reason)) {
    return REASONS[reason as StatusReason]
  }
  return { label: reason, sentence: `Reason code ${reason}.`, outcome: 'UNKNOWN' }
}
