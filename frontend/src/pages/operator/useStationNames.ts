import { useQuery } from '@tanstack/react-query'
import { stationApi } from '../../services/endpoints'

/** The whole network, shared by the map, the directory and every ledger that names stations. */
export function useStations() {
  return useQuery({ queryKey: ['stations'], queryFn: stationApi.all, staleTime: 60_000 })
}

/** Station names by id, for ledgers that carry only station ids. */
export function useStationNames(): Map<number, string> {
  const stations = useStations()
  return new Map(stations.data?.map((station) => [station.id, station.name]) ?? [])
}
