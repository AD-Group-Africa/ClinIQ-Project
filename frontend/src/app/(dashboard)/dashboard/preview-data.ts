/**
 * DESIGN PREVIEW ONLY — NOT REAL DATA.
 *
 * These values exist so the foundation dashboard can show the visual system (stat cards, table, status chips).
 * They are not patients, not API responses and must never be used by a real feature.
 * Delete this file when the Overview page is connected to the ClinIQ API.
 */
import type { Tone } from '@/components/ui/Badge';

export interface PreviewQueueRow {
  id: string;
  name: string;
  reference: string;
  phone: string;
  time: string;
  statusLabel: string;
  statusTone: Tone;
}

export const previewQueueRows: PreviewQueueRow[] = [
  { id: 'p1', name: 'Sample patient 1', reference: 'SAMPLE-001', phone: '07XX XXX XXX', time: '08:05', statusLabel: 'Waiting', statusTone: 'warning' },
  { id: 'p2', name: 'Sample patient 2', reference: 'SAMPLE-002', phone: '07XX XXX XXX', time: '08:20', statusLabel: 'In consultation', statusTone: 'info' },
  { id: 'p3', name: 'Sample patient 3', reference: 'SAMPLE-003', phone: '07XX XXX XXX', time: '08:32', statusLabel: 'Completed', statusTone: 'success' },
  { id: 'p4', name: 'Sample patient 4', reference: 'SAMPLE-004', phone: '07XX XXX XXX', time: '08:41', statusLabel: 'Payment overdue', statusTone: 'danger' },
];
