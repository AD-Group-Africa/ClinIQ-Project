import { Badge, type Tone } from './Badge';

export interface StatusChipProps {
  tone: Tone;
  /** The status text shown to everyone (and read by screen readers). Never pass an empty label. */
  children: string;
}

/**
 * A workflow status such as "Waiting" or "Completed": a Badge with a dot.
 * It is deliberately generic — it maps a tone to a label and does NOT know backend status values.
 * Feature code decides which tone each real API status uses once the enums are confirmed.
 */
export function StatusChip({ tone, children }: StatusChipProps) {
  return (
    <Badge tone={tone} dot>
      {children}
    </Badge>
  );
}
