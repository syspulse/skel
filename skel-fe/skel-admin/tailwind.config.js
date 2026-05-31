import typography from '@tailwindcss/typography';

/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
          hover: 'hsl(var(--muted-hover))',
        },
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        header: {
          DEFAULT: 'hsl(var(--header))',
          fg: 'hsl(var(--header-fg))',
          'fg-muted': 'hsl(var(--header-fg-muted))',
        },
        nav: {
          DEFAULT: 'hsl(var(--nav))',
          active: 'hsl(var(--nav-active))',
          fg: 'hsl(var(--nav-fg))',
          'fg-muted': 'hsl(var(--nav-fg-muted))',
        },
      },
    },
  },
  plugins: [typography],
};
