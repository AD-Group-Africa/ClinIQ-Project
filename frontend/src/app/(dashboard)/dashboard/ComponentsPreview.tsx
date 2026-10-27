'use client';

import { useState } from 'react';
import { IconPlus } from '@tabler/icons-react';
import { Alert } from '@/components/feedback/Alert';
import { ConfirmationDialog } from '@/components/feedback/ConfirmationDialog';
import { EmptyState } from '@/components/feedback/EmptyState';
import { ErrorState } from '@/components/feedback/ErrorState';
import { Loading } from '@/components/feedback/Loading';
import { Skeleton } from '@/components/feedback/Skeleton';
import { notify } from '@/components/feedback/Toast';
import { FormField } from '@/components/forms/FormField';
import { FormSection } from '@/components/forms/FormSection';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Checkbox } from '@/components/ui/Checkbox';
import { Drawer } from '@/components/ui/Drawer';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Radio } from '@/components/ui/Radio';
import { SearchField } from '@/components/ui/SearchField';
import { Select } from '@/components/ui/Select';
import { StatusChip } from '@/components/ui/StatusChip';
import { Switch } from '@/components/ui/Switch';
import { Tabs } from '@/components/ui/Tabs';
import { Textarea } from '@/components/ui/Textarea';

/** Design-preview only: shows every shared component in its states. Not part of any real workflow. */
export function ComponentsPreview() {
  return (
    <Card>
      <CardHeader
        title="Component preview"
        description="The shared building blocks every ClinIQ screen will use."
        headingLevel={2}
        actions={<Badge tone="warning">Design preview</Badge>}
      />
      <CardBody>
        <Tabs
          label="Component groups"
          tabs={[
            { id: 'buttons', label: 'Buttons & chips', content: <ButtonsPreview /> },
            { id: 'forms', label: 'Form controls', content: <FormsPreview /> },
            { id: 'feedback', label: 'Feedback states', content: <FeedbackPreview /> },
            { id: 'overlays', label: 'Dialogs & toasts', content: <OverlaysPreview /> },
          ]}
        />
      </CardBody>
    </Card>
  );
}

function ButtonsPreview() {
  const [saving, setSaving] = useState(false);

  const fakeSave = () => {
    setSaving(true);
    window.setTimeout(() => setSaving(false), 1800);
  };

  return (
    <div className="grid gap-8">
      <div>
        <h3 className="type-card-title mb-3">Buttons</h3>
        <div className="flex flex-wrap items-center gap-3">
          <Button leadingIcon={<IconPlus size={16} aria-hidden="true" />}>Primary</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="danger">Danger</Button>
          <Button size="sm">Small</Button>
          <Button disabled>Disabled</Button>
          <Button loading={saving} onClick={fakeSave}>
            {saving ? 'Saving…' : 'Click to see loading'}
          </Button>
        </div>
      </div>
      <div>
        <h3 className="type-card-title mb-3">Status chips and badges</h3>
        <div className="flex flex-wrap items-center gap-3">
          <StatusChip tone="warning">Waiting</StatusChip>
          <StatusChip tone="info">In consultation</StatusChip>
          <StatusChip tone="success">Completed</StatusChip>
          <StatusChip tone="danger">Payment overdue</StatusChip>
          <StatusChip tone="neutral">Cancelled</StatusChip>
          <Badge tone="neutral">Badge</Badge>
        </div>
        <p className="type-helper mt-3">Every chip carries text and a dot, so the status is never shown by colour alone.</p>
      </div>
    </div>
  );
}

function FormsPreview() {
  const [nameError, setNameError] = useState(true);
  const [remind, setRemind] = useState(true);
  const [search, setSearch] = useState('');

  return (
    <div className="grid gap-8 md:grid-cols-2">
      <FormSection title="Fields" description="Labels, hints and errors are linked for screen readers.">
        <FormField label="Full name" required hint="As it appears on the patient's ID." error={nameError ? 'Enter a full name.' : undefined}>
          {(control) => <Input {...control} placeholder="Enter a name" onChange={(event) => setNameError(event.target.value.trim() === '')} />}
        </FormField>
        <FormField label="Visit type">
          {(control) => (
            <Select {...control} defaultValue="">
              <option value="" disabled>
                Select a visit type
              </option>
              <option>Consultation</option>
              <option>Follow-up</option>
            </Select>
          )}
        </FormField>
        <FormField label="Notes">{(control) => <Textarea {...control} placeholder="Optional notes" />}</FormField>
      </FormSection>

      <div className="grid content-start gap-8">
        <FormSection title="Choices">
          <Checkbox label="Send an SMS reminder" description="Example checkbox with helper text." defaultChecked />
          <div role="radiogroup" aria-label="Contact preference" className="grid">
            <Radio name="contact" label="Phone call" defaultChecked />
            <Radio name="contact" label="SMS" />
          </div>
          <Switch label="Show completed items" description="Applies immediately." checked={remind} onCheckedChange={setRemind} />
        </FormSection>
        <FormSection title="Search">
          <SearchField label="Search the sample list" placeholder="Search by name or number" value={search} onChange={(event) => setSearch(event.target.value)} />
        </FormSection>
      </div>
    </div>
  );
}

function FeedbackPreview() {
  return (
    <div className="grid gap-8">
      <div className="grid gap-3">
        <Alert tone="info" title="Information">Neutral guidance for the user.</Alert>
        <Alert tone="success" title="Success">The change was saved.</Alert>
        <Alert tone="warning" title="Warning">Stock for this item is running low.</Alert>
        <Alert tone="danger" title="Error">This action could not be completed.</Alert>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <Loading label="Loading queue…" />
        </Card>
        <Card className="grid gap-3 p-6" aria-hidden="true">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </Card>
        <Card>
          <EmptyState title="Nothing here yet" description="When patients are added to the queue they will appear here." />
        </Card>
        <Card>
          <ErrorState onRetry={() => notify.info('Retry pressed (preview only)')} />
        </Card>
      </div>
    </div>
  );
}

function OverlaysPreview() {
  const [modalOpen, setModalOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const confirm = () => {
    setBusy(true);
    window.setTimeout(() => {
      setBusy(false);
      setConfirmOpen(false);
      notify.success('Preview action confirmed');
    }, 1200);
  };

  return (
    <div className="flex flex-wrap items-center gap-3">
      <Button variant="secondary" onClick={() => setModalOpen(true)}>Open dialog</Button>
      <Button variant="secondary" onClick={() => setDrawerOpen(true)}>Open drawer</Button>
      <Button variant="danger" onClick={() => setConfirmOpen(true)}>Open confirmation</Button>
      <Button variant="secondary" onClick={() => notify.success('Saved successfully')}>Success toast</Button>
      <Button variant="secondary" onClick={() => notify.error('Could not save. Please try again.')}>Error toast</Button>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Example dialog"
        description="Focus stays inside this dialog. Press Escape or use the buttons to close it."
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button onClick={() => setModalOpen(false)}>Save</Button>
          </>
        }
      >
        <FormField label="Example field">{(control) => <Input {...control} />}</FormField>
      </Modal>

      <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)} label="Example drawer" side="right">
        <div className="p-6 pt-16">
          <h2 className="type-section-title">Example drawer</h2>
          <p className="type-body mt-2 text-muted">Side panels are used for detail views that should not leave the current page.</p>
        </div>
      </Drawer>

      <ConfirmationDialog
        open={confirmOpen}
        title="Remove this item?"
        description="This is a preview. The confirm button shows a busy state that blocks a second click."
        confirmLabel="Remove"
        tone="danger"
        busy={busy}
        onConfirm={confirm}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}
