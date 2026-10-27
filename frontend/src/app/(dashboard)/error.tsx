'use client';

import { ErrorState } from '@/components/feedback/ErrorState';
import { Card } from '@/components/ui/Card';

/** Shown inside the shell if a page in this group crashes. The error is not logged here to avoid leaking patient data to the console. */
export default function DashboardError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <Card>
      <ErrorState
        title="This page could not be displayed"
        description="Something went wrong while showing this page. Try again, and contact your administrator if it keeps happening."
        onRetry={reset}
      />
    </Card>
  );
}
