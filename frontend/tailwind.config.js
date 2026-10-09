/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        serif: ['"Playfair Display"', 'Georgia', 'ui-serif', 'serif'],
        sans: ['"Plus Jakarta Sans"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      colors: {
        cream: '#FAF8F5',
        ink: '#0F1720',
        muted: '#52606D',
        coral: {
          DEFAULT: '#FF5A36',
          hover: '#E04826',
          pale: '#FFF0E7',
        },
        mist: '#F1ECE7',
        sage: {
          DEFAULT: '#059669',
          pale: '#E1F1E9',
        },
        mustard: {
          DEFAULT: '#D97706',
          pale: '#FFF2C9',
        },
        emerald: {
          positive: '#14805D',
        },
        negative: '#D34E34',
        hairline: 'rgba(15, 23, 32, 0.10)',
      },
      borderRadius: {
        card: '1.75rem',
        input: '0.875rem',
      },
      boxShadow: {
        soft: '0 1px 2px rgba(15,23,32,0.04), 0 8px 24px -12px rgba(15,23,32,0.10)',
        lift: '0 2px 4px rgba(15,23,32,0.04), 0 20px 40px -18px rgba(15,23,32,0.18)',
        pressed: 'inset 0 1px 2px rgba(15,23,32,0.08)',
        ring: '0 0 0 3px rgba(255,90,54,0.22)',
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'scale-in': {
          '0%': { opacity: '0', transform: 'scale(0.96)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        'slide-in-right': {
          '0%': { transform: 'translateX(100%)' },
          '100%': { transform: 'translateX(0)' },
        },
        'slide-up-sheet': {
          '0%': { transform: 'translateY(100%)' },
          '100%': { transform: 'translateY(0)' },
        },
      },
      animation: {
        'fade-up': 'fade-up 250ms cubic-bezier(0.22, 0.61, 0.36, 1) both',
        'fade-in': 'fade-in 200ms ease-out both',
        'scale-in': 'scale-in 200ms cubic-bezier(0.22, 0.61, 0.36, 1) both',
        'slide-in-right': 'slide-in-right 260ms cubic-bezier(0.22, 0.61, 0.36, 1) both',
        'slide-up-sheet': 'slide-up-sheet 260ms cubic-bezier(0.22, 0.61, 0.36, 1) both',
      },
    },
  },
  plugins: [],
}
