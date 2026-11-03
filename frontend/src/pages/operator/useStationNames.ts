import { useQuery } from '@tanstack/react-query'
import { stationApi } from '../../services/endpoints'

/** Station names by id, for ledgers that carry only station ids. */
export function useStations() {
  return useQuery({ queryKey: ['stations'], queryFn: () => stationApi.list() })
}

export function useStationNames(): Map<number, string> {
  const stations = useStations()
  return new Map(stations.data?.content.map((station) => [station.id, station.name]) ?? [])
}
