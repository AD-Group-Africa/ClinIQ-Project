import type { Metadata, Viewport } from 'next';
import localFont from 'next/font/local';
import './globals.css';
import { AuthProvider } from '@/lib/auth/AuthContext';

// Fonts are self-hosted (Latin subset, SIL Open Font License): no third-party request at page load,
// which also helps on slow or restricted networks.
const workSans = localFont({
  src: './fonts/WorkSans-Variable.woff2',
  variable: '--font-work-sans',
  weight: '100 900',
  display: 'swap',
});

const sourceSerif = localFont({
  src: './fonts/SourceSerif4-Variable.woff2',
  variable: '--font-source-serif',
  weight: '200 900',
  display: 'swap',
});

export const metadata: Metadata = {
  title: { default: 'ClinIQ', template: '%s · ClinIQ' },
  description: 'ClinIQ clinic management platform',
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: '#08233d',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${workSans.variable} ${sourceSerif.variable}`}>
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}