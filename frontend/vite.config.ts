import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    coverage: {
      provider: 'v8', reporter: ['text', 'html'],
      thresholds: { lines: 80, statements: 80, functions: 75, branches: 75 },
      include: ['src/**/*.{ts,tsx}'], exclude: ['src/main.tsx', 'src/test/**']
    }
  }
});
