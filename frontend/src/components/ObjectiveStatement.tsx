/** The objective statement (architecture §6), verbatim wherever allocation results appear. */
export function ObjectiveStatement() {
  return (
    <aside role="note" aria-label="Objective" className="rounded-lg border border-border px-5 py-4 text-sm leading-relaxed">
      <p className="font-medium text-text">What the algorithms optimise</p>
      <p className="mt-1 max-w-[75ch] text-muted">
        Matching maximises the <em className="text-text">number</em> of request–connector assignments in the modeled
        compatibility graph. Edmonds-Karp maximises the <em className="text-text">total feasible power</em> in the modeled
        network. Neither minimises waiting time or guarantees fairness.
      </p>
    </aside>
  )
}
