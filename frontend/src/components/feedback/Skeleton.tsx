import { cn } from '@/lib/utils/cn';

/** Grey placeholder block shown while content loads. Size it with className (for example "h-4 w-40"). */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn('animate-pulse rounded-control bg-line-subtle', className)} />;
}
