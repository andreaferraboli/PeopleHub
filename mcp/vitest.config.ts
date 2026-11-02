import { defineConfig } from 'vitest/config';
import path from 'node:path';

export default defineConfig({
  resolve: {
    alias: {
      'cloudflare:workers': path.resolve(__dirname, 'src/node/cloudflare-workers-shim.ts'),
    },
  },
  test: {
    environment: 'node',
    // La libreria OAuth importa "cloudflare:workers": va elaborata da Vite per usare lo stub.
    server: { deps: { inline: ['@cloudflare/workers-oauth-provider'] } },
    include: ['test/**/*.test.ts'],
    testTimeout: 30_000,
    hookTimeout: 30_000,
    // Un solo emulatore Firestore condiviso: i file di test girano uno alla volta.
    fileParallelism: false,
  },
});
