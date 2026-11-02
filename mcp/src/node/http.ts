/**
 * Adattatore HTTP Node -> handler fetch del Worker (Request/Response standard).
 * Pensato per stare dietro a un reverse proxy locale (Traefik) che termina il TLS:
 * l'URL della richiesta si costruisce SEMPRE dall'origine pubblica configurata, mai dall'Host o
 * da un URL assoluto mandato dal client; l'IP del client arriva da X-Real-Ip solo se la connessione
 * viene dal proxy in loopback.
 */
import type { IncomingMessage, ServerResponse } from 'node:http';
import { Readable } from 'node:stream';
import type { Env } from '../config.js';

export interface FetchWorker {
  fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response>;
}

export interface NodeHandlerOptions {
  /** Origine pubblica (https://people-mcp.<ip>.sslip.io), senza barra finale. */
  publicUrl: string;
  /** Tetto al corpo delle richieste (byte). */
  maxBodyBytes?: number;
  /** Errori inattesi (default console.error, senza dettagli della richiesta). */
  onError?: (error: unknown) => void;
}

// Intestazioni da non passare al Worker: di trasporto, o riservate all'IP del client.
const DROP_REQUEST_HEADERS = new Set([
  'connection', 'keep-alive', 'proxy-connection', 'transfer-encoding', 'upgrade', 'te', 'trailer', 'expect',
  'content-length', 'host', 'cf-connecting-ip',
]);
const DROP_RESPONSE_HEADERS = new Set(['connection', 'keep-alive', 'transfer-encoding', 'content-length', 'set-cookie']);

const LOOPBACK = new Set(['127.0.0.1', '::1', '::ffff:127.0.0.1']);

/** IP del client: X-Real-Ip (lo scrive Traefik) solo se il socket viene dal proxy locale. */
export function clientIp(req: IncomingMessage): string {
  const remote = req.socket.remoteAddress ?? '';
  if (LOOPBACK.has(remote)) {
    const real = req.headers['x-real-ip'];
    const value = Array.isArray(real) ? real[0] : real;
    if (value && /^[0-9a-fA-F:.]{2,45}$/.test(value.trim())) return value.trim();
  }
  return remote || 'unknown';
}

class BodyTooLarge extends Error {}

async function readBody(req: IncomingMessage, max: number): Promise<Buffer> {
  const declared = Number(req.headers['content-length'] ?? 0);
  if (declared > max) throw new BodyTooLarge();
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += (chunk as Buffer).length;
    if (size > max) throw new BodyTooLarge();
    chunks.push(chunk as Buffer);
  }
  return Buffer.concat(chunks);
}

export function toWebRequest(req: IncomingMessage, publicUrl: string, body: Buffer | undefined, signal?: AbortSignal): Request {
  const target = req.url ?? '/';
  if (!target.startsWith('/')) throw new TypeError('request-target non valido');
  const headers = new Headers();
  for (const [name, value] of Object.entries(req.headersDistinct)) {
    if (!value || DROP_REQUEST_HEADERS.has(name)) continue;
    for (const v of value) headers.append(name, v);
  }
  headers.set('CF-Connecting-IP', clientIp(req));
  const method = (req.method ?? 'GET').toUpperCase();
  return new Request(publicUrl + target, {
    method,
    headers,
    body: method === 'GET' || method === 'HEAD' || !body?.length ? undefined : new Uint8Array(body),
    redirect: 'manual',
    ...(signal ? { signal } : {}),
  });
}

async function writeWebResponse(res: ServerResponse, response: Response, isHead: boolean): Promise<void> {
  res.statusCode = response.status;
  response.headers.forEach((value, name) => {
    if (!DROP_RESPONSE_HEADERS.has(name)) res.setHeader(name, value);
  });
  const cookies = response.headers.getSetCookie();
  if (cookies.length) res.setHeader('Set-Cookie', cookies);
  if (!res.hasHeader('strict-transport-security')) res.setHeader('Strict-Transport-Security', 'max-age=31536000');
  if (isHead || !response.body) {
    res.end();
    return;
  }
  await new Promise<void>((resolve, reject) => {
    const stream = Readable.fromWeb(response.body as import('node:stream/web').ReadableStream);
    stream.on('error', reject);
    res.on('close', resolve);
    stream.pipe(res);
  });
}

function plain(res: ServerResponse, status: number, text: string): void {
  if (res.headersSent) {
    res.destroy();
    return;
  }
  res.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(text);
}

/** Handler per `http.createServer`: un ExecutionContext nuovo per ogni richiesta (la libreria OAuth ci scrive i props). */
export function createNodeHandler(worker: FetchWorker, env: Env, options: NodeHandlerOptions) {
  const publicUrl = options.publicUrl.replace(/\/+$/, '');
  const maxBody = options.maxBodyBytes ?? 4 * 1024 * 1024;
  const onError = options.onError ?? ((e: unknown) => console.error('[http] errore', e instanceof Error ? e.message : 'sconosciuto'));
  const pending = new Set<Promise<unknown>>();

  const handler = async (req: IncomingMessage, res: ServerResponse): Promise<void> => {
    try {
      const method = (req.method ?? 'GET').toUpperCase();
      let body: Buffer | undefined;
      if (method !== 'GET' && method !== 'HEAD') body = await readBody(req, maxBody);
      // il client chiude la connessione prima della risposta: request.signal si annulla (l'assistente smette di chiamare i modelli)
      const gone = new AbortController();
      res.on('close', () => {
        if (!res.writableFinished) gone.abort();
      });
      let request: Request;
      try {
        request = toWebRequest(req, publicUrl, body, gone.signal);
      } catch {
        plain(res, 400, 'Bad Request');
        return;
      }
      const ctx = {
        waitUntil(p: Promise<unknown>) {
          const tracked = Promise.resolve(p).catch(onError).finally(() => pending.delete(tracked));
          pending.add(tracked);
        },
        passThroughOnException() {},
        props: {},
      } as unknown as ExecutionContext;
      const response = await worker.fetch(request, env, ctx);
      await writeWebResponse(res, response, method === 'HEAD');
    } catch (error) {
      if (error instanceof BodyTooLarge) {
        plain(res, 413, 'Payload Too Large');
        return;
      }
      onError(error);
      plain(res, 500, 'Internal Server Error');
    }
  };
  /** Attende i lavori in background (waitUntil) prima di chiudere. */
  handler.drain = () => Promise.allSettled([...pending]);
  return handler;
}
