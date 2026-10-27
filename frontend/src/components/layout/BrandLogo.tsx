import Image from 'next/image';
import { cn } from '@/lib/utils/cn';

export interface BrandLogoProps {
  /** "full" = symbol + wordmark, "mark" = symbol only (for tight spaces). */
  variant?: 'full' | 'mark';
  className?: string;
}

/**
 * The official ClinIQ logo, unmodified. Files live in /public/brand.
 * The wordmark is dark navy, so it must sit on a light surface: on the navy sidebar it is placed
 * on a white plate (see Sidebar). Do not recolour or redraw it; ask for a reversed logo from the brand owner instead.
 * `unoptimized` serves the file as-is (small, pre-sized PNGs), so no image-optimiser dependency is needed.
 */
export function BrandLogo({ variant = 'full', className }: BrandLogoProps) {
  if (variant === 'mark') {
    return <Image src="/brand/cliniq-mark.png" alt="ClinIQ" width={128} height={128} unoptimized className={cn('h-9 w-9', className)} />;
  }
  return <Image src="/brand/cliniq-logo.png" alt="ClinIQ" width={480} height={164} unoptimized className={cn('h-10 w-auto', className)} />;
}
