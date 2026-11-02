// Genera gli asset del server: lo script della pagina di login (src/generated/loginJs.ts).
// Con --node impacchetta anche il server Node per la VPS in dist/server.mjs (un solo file, nessun node_modules a runtime).
import { build } from 'esbuild';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const result = await build({
  entryPoints: [path.join(root, 'src', 'web', 'login.ts')],
  write: false,
  bundle: true,
  platform: 'browser',
  target: 'es2020',
  format: 'esm',
  minify: true,
  logLevel: 'info',
});
mkdirSync(path.join(root, 'src', 'generated'), { recursive: true });
writeFileSync(
  path.join(root, 'src', 'generated', 'loginJs.ts'),
  `// GENERATO da build.mjs (non modificare, non e' nel repo)\nexport const LOGIN_JS = ${JSON.stringify(result.outputFiles[0].text)};\n`
);

if (process.argv.includes('--node')) {
  await build({
    entryPoints: [path.join(root, 'src', 'node', 'main.ts')],
    outfile: path.join(root, 'dist', 'server.mjs'),
    bundle: true,
    platform: 'node',
    target: 'node22',
    format: 'esm',
    sourcemap: 'linked',
    // la libreria OAuth importa "cloudflare:workers": in Node basta il sostituto
    alias: { 'cloudflare:workers': path.join(root, 'src', 'node', 'cloudflare-workers-shim.ts') },
    // alcune dipendenze CommonJS usano require(): in un bundle ESM serve definirlo
    banner: { js: "import { createRequire as __phCreateRequire } from 'node:module'; const require = __phCreateRequire(import.meta.url);" },
    tsconfig: path.join(root, 'tsconfig.json'),
    legalComments: 'none',
    logLevel: 'info',
  });
}
