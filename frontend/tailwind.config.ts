import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        navy: {
          DEFAULT: "#1a365d",
          light: "#2c5282",
          dark: "#0f2440",
        },
        gold: {
          DEFAULT: "#c6a052",
          light: "#d4b06a",
        },
        background: "var(--background)",
        foreground: "var(--foreground)",
      },
      boxShadow: {
        card: "0 1px 3px rgba(15,36,64,0.08), 0 1px 2px rgba(15,36,64,0.04)",
        lift: "0 12px 30px -12px rgba(15,36,64,0.25)",
      },
    },
  },
  plugins: [],
};
export default config;
