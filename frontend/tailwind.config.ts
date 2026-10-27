import type { Config } from 'tailwindcss';

/**
 * ClinIQ Tailwind configuration.
 *
 * Every colour, radius, shadow and gradient here points at a CSS variable defined in
 * src/app/globals.css. That file is the single place where raw values live, so a design
 * change means editing one file. Tailwind's default palette is kept (extend, not replace)
 * so existing pages that use classes like bg-gray-100 keep working.
 */
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      screens: {
        // Breakpoints taken from the Canva reception mockup (mobile layout at <= 850px / <= 560px).
        xs: '561px',
        shell: '851px',
      },
      colors: {
        background: 'var(--color-background)',
        surface: {
          DEFAULT: 'var(--color-surface)',
          muted: 'var(--color-surface-muted)',
        },
        ink: 'var(--color-text-primary)',
        muted: 'var(--color-text-secondary)',
        brand: {
          DEFAULT: 'var(--color-brand)',
          dark: 'var(--color-brand-dark)',
          light: 'var(--color-brand-light)',
        },
        accent: 'var(--color-accent)',
        navy: {
          DEFAULT: 'var(--color-navy)',
          soft: 'var(--color-navy-soft)',
          line: 'var(--color-navy-line)',
        },
        'on-navy': {
          DEFAULT: 'var(--color-on-navy)',
          muted: 'var(--color-on-navy-muted)',
        },
        line: {
          DEFAULT: 'var(--color-border)',
          subtle: 'var(--color-border-subtle)',
          input: 'var(--color-border-input)',
        },
        focus: 'var(--color-focus)',
        overlay: 'var(--color-overlay)',
        'nav-hover': 'var(--color-nav-hover)',
        info: { DEFAULT: 'var(--color-info)', soft: 'var(--color-info-soft)' },
        success: { DEFAULT: 'var(--color-success)', soft: 'var(--color-success-soft)' },
        warning: { DEFAULT: 'var(--color-warning)', soft: 'var(--color-warning-soft)' },
        danger: {
          DEFAULT: 'var(--color-danger)',
          soft: 'var(--color-danger-soft)',
          'on-dark': 'var(--color-danger-on-dark)',
        },
        plain: { DEFAULT: 'var(--color-neutral)', soft: 'var(--color-neutral-soft)' },
      },
      fontFamily: {
        sans: ['var(--font-work-sans)', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'sans-serif'],
        heading: ['var(--font-source-serif)', 'Georgia', 'ui-serif', 'serif'],
      },
      borderRadius: {
        mark: 'var(--radius-mark)',
        control: 'var(--radius-control)',
        tile: 'var(--radius-tile)',
        card: 'var(--radius-card)',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
        lift: 'var(--shadow-lift)',
        drawer: 'var(--shadow-drawer)',
        toast: 'var(--shadow-toast)',
        halo: 'var(--shadow-halo)',
        'nav-indicator': 'inset 3px 0 0 var(--color-accent)',
      },
      backgroundImage: {
        'nav-active': 'var(--gradient-nav-active)',
        'topbar-overlay': 'var(--gradient-topbar-overlay)',
      },
      keyframes: {
        reveal: {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'dialog-in': {
          from: { opacity: '0', transform: 'translateY(8px) scale(0.98)' },
          to: { opacity: '1', transform: 'none' },
        },
        'drawer-in-left': {
          from: { transform: 'translateX(-100%)' },
          to: { transform: 'translateX(0)' },
        },
        'drawer-in-right': {
          from: { transform: 'translateX(100%)' },
          to: { transform: 'translateX(0)' },
        },
      },
      animation: {
        reveal: 'reveal 0.32s ease both',
        'dialog-in': 'dialog-in 0.2s ease-out both',
        'drawer-in-left': 'drawer-in-left 0.25s ease both',
        'drawer-in-right': 'drawer-in-right 0.25s ease both',
      },
    },
  },
  plugins: [],
};

export default config;
