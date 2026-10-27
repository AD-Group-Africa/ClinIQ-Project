
'use client';

import { useEffect, useState } from 'react';
import { IconUserPlus } from '@tabler/icons-react';

import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/feedback/EmptyState';
import { ErrorState } from '@/components/feedback/ErrorState';
import { Loading } from '@/components/feedback/Loading';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import {
  DataTable,
  type DataTableColumn,
} from '@/components/ui/DataTable';
import { SearchField } from '@/components/ui/SearchField';

import { searchPatients } from '@/lib/patientApi';
import type {
  PatientListMeta,
  PatientResponse,
} from '@/types/patient';

const PAGE_SIZE = 20;
const DEBOUNCE_DELAY = 350;

function formatDate(value: string | null): string {
  if (!value) return '—';

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return '—';

  return date.toLocaleDateString();
}

function formatGender(gender: PatientResponse['gender']): string {
  if (!gender) return '—';

  return gender
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export default function PatientsPage() {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [page, setPage] = useState(0);

  const [patients, setPatients] = useState<PatientResponse[]>([]);
  const [meta, setMeta] = useState<PatientListMeta | null>(null);

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);

  // Wait briefly after typing before searching the backend.
  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
    }, DEBOUNCE_DELAY);

    return () => window.clearTimeout(timeoutId);
  }, [query]);

  // Load the requested page of patients.
  useEffect(() => {
    let active = true;

    async function loadPatients() {
      setIsLoading(true);
      setError(null);

      try {
        const result = await searchPatients({
          query: debouncedQuery,
          page,
          size: PAGE_SIZE,
        });

        if (!active) return;

        setPatients(result.patients);
        setMeta(result.meta);
      } catch (err: unknown) {
        if (!active) return;

        setPatients([]);
        setMeta(null);

        setError(
          err instanceof Error
            ? err.message
            : 'We could not load patients. Please try again.'
        );
      } finally {
        if (active) {
          setIsLoading(false);
        }
      }
    }

    void loadPatients();

    return () => {
      active = false;
    };
  }, [debouncedQuery, page, retryToken]);

  const columns: DataTableColumn<PatientResponse>[] = [
    {
      id: 'patientNumber',
      header: 'Patient No.',
      cell: (patient) => patient.patientNumber ?? '—',
    },
    {
      id: 'name',
      header: 'Patient Name',
      primary: true,
      cell: (patient) => (
        <span className="font-semibold text-ink">
          {patient.firstName} {patient.lastName}
        </span>
      ),
    },
    {
      id: 'phone',
      header: 'Phone',
      cell: (patient) => patient.phone || '—',
    },
    {
      id: 'email',
      header: 'Email',
      cell: (patient) => patient.email ?? '—',
    },
    {
      id: 'gender',
      header: 'Gender',
      cell: (patient) => formatGender(patient.gender),
    },
    {
      id: 'createdAt',
      header: 'Date Added',
      cell: (patient) => formatDate(patient.createdAt),
    },
  ];

  return (
    <div>
      <PageHeader
        kicker="PATIENT MANAGEMENT"
        title="Patients"
        description="Search and manage patient records across your clinic."
        actions={
          <Button
            disabled
            title="Patient registration will be enabled when the registration workflow is ready."
            leadingIcon={<IconUserPlus size={18} />}
          >
            Register Patient
          </Button>
        }
      />

      <Card>
        <CardHeader
          title="All Patients"
          description="Search by patient name, phone number, or other available details."
          actions={
            <Badge tone="neutral">
              {meta ? `${meta.total} patients` : 'Patient records'}
            </Badge>
          }
        />

        <div className="p-4 sm:p-6">
          <SearchField
            label="Search patients"
            placeholder="Search patients..."
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            className="w-full"
          />
        </div>

        {isLoading ? (
          <Loading label="Loading patients..." />
        ) : error ? (
          <ErrorState
            title="Could not load patients"
            description={error}
            onRetry={() => setRetryToken((current) => current + 1)}
            retryLabel="Try again"
          />
        ) : patients.length === 0 ? (
          <EmptyState
            icon={IconUserPlus}
            title={
              debouncedQuery
                ? 'No matching patients'
                : 'No patients found'
            }
            description={
              debouncedQuery
                ? 'Try another search term and check the spelling.'
                : 'There are no patient records to display yet.'
            }
          />
        ) : (
          <>
            <DataTable
              caption="Patient records"
              columns={columns}
              rows={patients}
              getRowId={(patient) => patient.id}
            />

            <div className="flex flex-col gap-3 border-t border-line p-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
              <p className="text-sm text-muted" aria-live="polite">
                {meta
                  ? `Showing ${page * PAGE_SIZE + 1}–${Math.min(
                      page * PAGE_SIZE + patients.length,
                      meta.total
                    )} of ${meta.total} patients`
                  : 'Patient records'}
              </p>

              <div className="flex items-center gap-3">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((current) => current - 1)}
                >
                  Previous
                </Button>

                <span className="text-sm text-muted">
                  Page {page + 1} of {Math.max(meta?.totalPages ?? 0, 1)}
                </span>

                <Button
                  variant="secondary"
                  size="sm"
                  disabled={!meta || page + 1 >= meta.totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          </>
        )}
      </Card>
    </div>
  );
}
