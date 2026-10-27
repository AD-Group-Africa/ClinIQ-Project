import type { Metadata } from 'next';
import { IconCalendar, IconUsers } from '@tabler/icons-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { Alert } from '@/components/feedback/Alert';
import { EmptyState } from '@/components/feedback/EmptyState';
import { Badge } from '@/components/ui/Badge';
import { Card, CardHeader } from '@/components/ui/Card';

export const metadata: Metadata = { title: 'Overview' };

/**
 * Clinic overview dashboard.
 *
 * Neither section below is connected to the ClinIQ API yet. There is no confirmed, non-admin
 * endpoint for queue/appointment summary metrics, and the response shape of the appointments and
 * queue endpoints (GET /appointments/today, GET /appointments/queue) has not been confirmed either
 * — so nothing here is fetched, and nothing here is fabricated. Each EmptyState below explains
 * specifically what is missing. See the ClinIQ frontend audit for the full endpoint gap list.
 */
export default function OverviewPage() {
  return (
    <div className="animate-reveal">
      <PageHeader
        breadcrumbs={[{ label: 'ClinIQ', href: '/dashboard' }, { label: 'Overview' }]}
        kicker="ClinIQ workspace"
        title="Overview"
        description="A summary of today's clinic activity."
      />

      <Alert tone="info" title="Dashboard data is not yet connected" className="mb-7">
        The sections below will show real clinic numbers once their backend endpoints are confirmed.
        Nothing on this page is sample or fabricated data.
      </Alert>

      <div className="mb-7 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader
            title="Clinic summary"
            description="Waiting queue, today's appointments, and average wait time."
            headingLevel={2}
            actions={<Badge tone="neutral">Not connected</Badge>}
          />
          <EmptyState
            icon={IconUsers}
            title="No summary endpoint yet"
            description="There is no confirmed, non-admin backend endpoint for clinic summary metrics. This card will populate once one is available."
            headingLevel={3}
          />
        </Card>

        <Card>
          <CardHeader
            title="Today's queue"
            description="Patients currently waiting or in consultation."
            headingLevel={2}
            actions={<Badge tone="neutral">Not connected</Badge>}
          />
          <EmptyState
            icon={IconCalendar}
            title="Queue response shape not confirmed"
            description="GET /appointments/today and GET /appointments/queue exist on the backend, but their response fields (patient name, status values, timestamps) have not been confirmed, so this list is not built yet rather than guessed."
            headingLevel={3}
          />
        </Card>
      </div>
    </div>
  );
}