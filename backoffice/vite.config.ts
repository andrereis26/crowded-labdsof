/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  // Deployed behind the router at /backoffice/ (deployment/router/nginx.conf);
  // the dev server keeps serving at /.
  base: command === 'build' ? '/backoffice/' : '/',
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
      reporter: ['text-summary', 'lcov'],
      // ADR 0011: pull requests fail below 80% line coverage.
      thresholds: { lines: 80 },
    },
  },
}))
