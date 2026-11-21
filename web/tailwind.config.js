/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Be Vietnam Pro"', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      colors: {
        canvas: '#F7F4EE',
        surface: '#FDFBF7',
        raised: '#EFE9DF',
        ink: {
          DEFAULT: '#1F1B16',
          secondary: '#5E554A',
          muted: '#7B7266',
        },
        forest: {
          50: '#F0F7F2',
          100: '#DCF0E2',
          200: '#BBE0C7',
          600: '#1D703D',
          700: '#166534',
          800: '#14532D',
          900: '#0F3D22',
        },
        amberish: {
          50: '#FDF8ED',
          100: '#F9EBD0',
          200: '#F0D29C',
          700: '#B45309',
          800: '#92400E',
        },
        danger: {
          50: '#FEF2F2',
          100: '#FEE2E2',
          700: '#B91C1C',
        },
      },
      transitionTimingFunction: {
        smooth: 'cubic-bezier(0.22, 1, 0.36, 1)',
      },
      boxShadow: {
        card: '0 1px 2px rgba(31, 27, 22, 0.05)',
        float: '0 8px 24px rgba(31, 27, 22, 0.08)',
      },
    },
  },
  plugins: [],
};
