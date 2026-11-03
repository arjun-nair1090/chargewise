import type { UseQueryResult } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { errorMessage } from '../lib/errors'
import { Button, EmptyState } from './ui'

/** Skeleton rows shown while data loads; they take the shape of the content they stand in for. */
export function Skeleton({ rows = 4, className = '' }: { rows?: number; className?: string }) {
  return (
    <div role="status" aria-busy="true" aria-label="Loading" className={`flex flex-col gap-3 p-5 ${className}`}>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="h-5 animate-pulse rounded-sm bg-surface-raised" style={{ width: `${92 - index * 9}%` }} />
      ))}
    </div>
  )
}

/** Loading, error, empty and success states for one query, in one place. */
export function QueryState<T>({ query, isEmpty, empty, rows, children }: {
  query: UseQueryResult<T>
  isEmpty?: (data: T) => boolean
  empty?: ReactNode
  rows?: number
  children: (data: T) => ReactNode
}) {
  if (query.isPending) {
    return <Skeleton rows={rows} />
  }
  if (query.isError) {
    return (
      <div role="alert" className="flex flex-wrap items-center justify-between gap-3 p-5">
        <p className="text-sm text-danger">{errorMessage(query.error)}</p>
        <Button size="sm" onClick={() => void query.refetch()}>Try again</Button>
      </div>
    )
  }
  if (isEmpty?.(query.data)) {
    return <>{empty ?? <EmptyState title="Nothing here yet" />}</>
  }
  return <>{children(query.data)}</>
}
