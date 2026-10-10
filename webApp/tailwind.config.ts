import type { Config } from "tailwindcss";

// Every palette colour the app uses resolves to a CSS variable defined per
// theme in app/globals.css, so light and dark mode need no per-class `dark:` variants.
const THEMED_FAMILIES = ["slate", "zinc", "sky", "indigo", "emerald", "teal", "amber", "orange", "rose", "red", "purple"];
const STEPS = ["50", "100", "200", "300", "400", "500", "600", "700", "800", "900", "950"];

const themeVar = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;

const themedPalette = Object.fromEntries(
  THEMED_FAMILIES.map((family) => [
    family,
    Object.fromEntries(STEPS.map((step) => [step, themeVar(`${family}-${step}`)])),
  ])
);

export default {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        ...themedPalette,
        white: themeVar("white"),
        black: themeVar("black"),
        "on-accent": themeVar("on-accent"),
        app: themeVar("surface-app"),
        panel: themeVar("surface-panel"),
        raised: themeVar("surface-raised"),
        card: themeVar("surface-card"),
        code: themeVar("surface-code"),
      },
      fontFamily: {
        mono: [
          "JetBrains Mono",
          "ui-monospace",
          "SFMono-Regular",
          "Menlo",
          "Monaco",
          "Consolas",
          "monospace"
        ],
        sans: [
          "Inter",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "Roboto",
          "sans-serif"
        ]
      }
    },
  },
  plugins: [],
} satisfies Config;
