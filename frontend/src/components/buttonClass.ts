export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
export type ButtonSize = 'sm' | 'md' | 'lg'

const buttonVariant: Record<ButtonVariant, string> = {
  primary: 'bg-accent font-semibold text-accent-ink hover:bg-accent-hover',
  secondary: 'border border-border-strong text-text hover:bg-surface-raised',
  ghost: 'text-muted hover:bg-surface-raised hover:text-text',
  danger: 'border border-danger/50 text-danger hover:bg-danger/10',
}

const buttonSize: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-sm',
  md: 'h-10 px-4 text-sm',
  lg: 'h-12 px-6 text-base',
}

export function buttonClass(variant: ButtonVariant = 'secondary', size: ButtonSize = 'md') {
  return `inline-flex shrink-0 items-center justify-center gap-2 rounded-md transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-50 ${buttonSize[size]} ${buttonVariant[variant]}`
}
