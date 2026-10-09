import { cn } from '../util/cn.js'

export function Skeleton({ className, style }) {
  return <div className={cn('skeleton', className)} style={style} />
}

export function GroupCardSkeleton() {
  return (
    <div className="surface-paper p-6">
      <div className="flex items-center gap-2">
        <Skeleton className="h-5 w-16" />
        <Skeleton className="h-5 w-10" />
      </div>
      <Skeleton className="mt-5 h-7 w-2/3" />
      <div className="mt-5 flex items-center gap-3">
        <Skeleton className="h-9 w-24 rounded-full" />
        <Skeleton className="h-9 w-16 rounded-full" />
      </div>
      <div className="hairline-t mt-6 pt-5">
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="mt-2 h-6 w-2/3" />
      </div>
    </div>
  )
}

export function ExpenseRowSkeleton() {
  return (
    <div className="flex items-center gap-4 py-4">
      <Skeleton className="h-10 w-10 rounded-full" />
      <div className="flex-1">
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="mt-2 h-3 w-1/3" />
      </div>
      <Skeleton className="h-4 w-20" />
    </div>
  )
}
