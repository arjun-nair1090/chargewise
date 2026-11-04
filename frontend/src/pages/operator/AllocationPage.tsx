import { Play } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { QueryState } from '../../components/QueryState'
import { Button, EmptyState, FormError, PageHeader, Panel } from '../../components/ui'
import { errorMessage } from '../../lib/errors'
import { formatDateTime } from '../../lib/format'
import { operatorApi } from '../../services/endpoints'
import { AllocationOutcome } from './AllocationOutcome'
import { useStationNames } from './useStationNames'

function RunButton() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const run = useMutation({
    mutationFn: operatorApi.runAllocation,
    onSuccess: async (response) => {
      queryClient.setQueryData(['allocation', response.id], response)
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      await queryClient.invalidateQueries({ queryKey: ['operator'] })
      navigate(`/operator/allocation/${response.id}`)
    },
  })
  return (
    <div className="flex flex-col items-end gap-2">
      <Button variant="primary" size="lg" busy={run.isPending} onClick={() => run.mutate()}>
        {!run.isPending && <Play aria-hidden="true" className="size-4 fill-current" strokeWidth={2} />}
        Run allocation
      </Button>
      {run.isError && <FormError message={errorMessage(run.error)} />}
    </div>
  )
}

export function AllocationPage() {
  const params = useParams()
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: operatorApi.dashboard, enabled: params.runId === undefined })
  const runId = params.runId !== undefined ? Number(params.runId) : dashboard.data?.latestAllocationRunId ?? null
  const run = useQuery({
    queryKey: ['allocation', runId],
    queryFn: () => operatorApi.allocation(runId!),
    enabled: runId !== null,
  })
  const stationNames = useStationNames()

  const header = (
    <PageHeader
      title="Allocation"
      job={
        run.data
          ? `Run #${run.data.id} on ${formatDateTime(run.data.createdAt)}: pending and waitlisted requests from ${formatDateTime(run.data.intervalStart)} to ${formatDateTime(run.data.intervalEnd)}.`
          : 'Match pending requests to connectors, then share each station’s grid power with Edmonds-Karp.'
      }
      actions={<RunButton />}
    />
  )

  if (params.runId === undefined && dashboard.isSuccess && runId === null) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        <Panel>
          <EmptyState title="No allocation has run yet">
            Running allocation locks every pending and waitlisted request, builds the compatibility graph, finds a maximum
            matching and allocates power. Nothing changes until you press Run allocation.
          </EmptyState>
        </Panel>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      {header}
      {params.runId === undefined && dashboard.isPending ? null : (
        <QueryState query={run} rows={8}>
          {(data) => <AllocationOutcome key={data.id} run={data} stationNames={stationNames} />}
        </QueryState>
      )}
    </div>
  )
}
