import { REGIONS, inRegion, type Region } from '../lib/regions'
import type { StationResponse } from '../types/api'

/** A segmented control choosing which cluster of the network the map and list show. */
export function RegionSwitch({ value, onChange, stations }: {
  value: Region
  onChange: (region: Region) => void
  stations: StationResponse[]
}) {
  return (
    <div role="group" aria-label="Region" className="inline-flex rounded-md border border-border bg-bg p-0.5">
      {REGIONS.map((region) => {
        const active = region.id === value
        const count = stations.filter((station) => inRegion(station, region.id)).length
        return (
          <button
            key={region.id}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(region.id)}
            className={`inline-flex h-9 items-center gap-2 rounded-[4px] px-3 text-sm transition-colors duration-150 ${
              active ? 'bg-surface-raised font-medium text-text' : 'text-muted hover:text-text'
            }`}
          >
            {region.label}
            <span className="font-mono text-xs text-muted">{count}</span>
          </button>
        )
      })}
    </div>
  )
}
