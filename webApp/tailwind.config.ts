import type { Config } from "tailwindcss";

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
        background: "#090D16",
        surface: "#0F172A",
        "surface-card": "#131E35",
        "surface-hover": "#1E293B",
        "surface-border": "#1E293B",
        "surface-border-subtle": "rgba(255, 255, 255, 0.08)",
        primary: "#38BDF8",
        success: "#10B981",
        warning: "#F59E0B",
        danger: "#EF4444",
        iso: {
          card: "#F43F5E",
          amount: "#10B981",
          datetime: "#38BDF8",
          id: "#A855F7",
          response: "#F59E0B",
          terminal: "#14B8A6",
          emv: "#FB923C",
          institution: "#6366F1",
          security: "#E11D48",
          private: "#94A3B8",
          network: "#64748B"
        }
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
