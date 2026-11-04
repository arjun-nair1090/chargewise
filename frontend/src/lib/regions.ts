import type { StationResponse } from '../types/api'

/**
 * The demo network is two clusters of real stations: the Mumbai Metropolitan Region and Pune. A view filter, not
 * data: every station north of 18.8° N is in the Mumbai region (the same split the dataset generator uses).
 */
export type Region = 'ALL' | 'MUMBAI' | 'PUNE'

export const REGIONS: { id: Region; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'MUMBAI', label: 'Mumbai region' },
  { id: 'PUNE', label: 'Pune' },
]

const MUMBAI_REGION_MIN_LATITUDE = 18.8

export function inRegion(station: Pick<StationResponse, 'latitude'>, region: Region): boolean {
  if (region === 'ALL') {
    return true
  }
  const mumbai = station.latitude >= MUMBAI_REGION_MIN_LATITUDE
  return region === 'MUMBAI' ? mumbai : !mumbai
}
