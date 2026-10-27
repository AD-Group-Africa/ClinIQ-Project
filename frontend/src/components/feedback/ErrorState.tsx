import { IconAlertTriangle } from '@tabler/icons-react';
import { Button } from '@/components/ui/Button';

export interface ErrorStateProps {
  title?: string;
  description?: string;
  /** When provided, a retry button is shown. */
  onRetry?: () => void;
  retryLabel?: string;
}

/** Shown when a screen cannot load. Always human-readable, with a way to try again. */
export function ErrorState({
  title = 'Something went wrong',
  description = 'We could not load this information. Check your connection and try again.',
  onRetry,
  retryLabel = 'Try again',
}: ErrorStateProps) {
  return (
    <div role="alert" className="p-10 text-center">
      <div className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-danger-soft text-danger" aria-hidden="true">
        <IconAlertTriangle size={24} />
      </div>
      <h3 className="type-card-title mt-4">{title}</h3>
      <p className="type-body mx-auto mt-2 max-w-sm text-muted">{description}</p>
      {onRetry && (
        <div className="mt-5 flex justify-center">
          <Button variant="secondary" onClick={onRetry}>
            {retryLabel}
          </Button>
        </div>
      )}
    </div>
  );
}
