import type { BookingResponse } from '../types/api'
import { connectorLabel, formatKw } from './format'
import { describeReason } from './reasons'

/** The one sentence that explains where a request stands. */
export function outcomeSentence(booking: BookingResponse): string {
  if (booking.statusReason) {
    return describeReason(booking.statusReason).sentence
  }
  switch (booking.status) {
    case 'PENDING':
      return 'Waiting for the next allocation run.'
    case 'ASSIGNED':
      return `Assigned a ${connectorLabel(booking.connectorType)} connector at ${formatKw(booking.allocatedPowerKw)}.`
    case 'CANCELLED':
      return 'You cancelled this request.'
    case 'COMPLETED':
      return 'The charging window has ended.'
    default:
      return ''
  }
}
