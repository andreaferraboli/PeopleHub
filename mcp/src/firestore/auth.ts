/**
 * Token OAuth2 per le API Google da un service account (JWT RS256 firmato con
 * WebCrypto, scambiato su oauth2.googleapis.com). Il token resta in cache fino
 * a poco prima della scadenza.
 */
export interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
}

const SCOPE = 'https://www.googleapis.com/auth/datastore';

const b64url = (bytes: ArrayBuffer | Uint8Array): string => {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = '';
  for (const b of arr) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

function pemToDer(pem: string): Uint8Array {
  const body = pem.replace(/-----BEGIN [^-]+-----/, '').replace(/-----END [^-]+-----/, '').replace(/\s+/g, '');
  const bin = atob(body);
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

export class ServiceAccountAuth {
  private cached: { token: string; expiresAt: number } | null = null;
  private inflight: Promise<string> | null = null;
  private keyPromise: Promise<CryptoKey> | null = null;

  constructor(
    private readonly account: ServiceAccount,
    private readonly fetchImpl: typeof fetch = (i, init) => fetch(i, init),
    private readonly now: () => number = () => Date.now()
  ) {}

  static fromJson(json: string, fetchImpl?: typeof fetch): ServiceAccountAuth {
    const parsed = JSON.parse(json) as ServiceAccount;
    if (!parsed.client_email || !parsed.private_key) throw new Error('Service account non valido: mancano client_email o private_key');
    return new ServiceAccountAuth(parsed, fetchImpl);
  }

  getToken = async (): Promise<string> => {
    if (this.cached && this.cached.expiresAt - 60_000 > this.now()) return this.cached.token;
    if (!this.inflight) {
      this.inflight = this.fetchToken().finally(() => {
        this.inflight = null;
      });
    }
    return this.inflight;
  };

  private key(): Promise<CryptoKey> {
    this.keyPromise ??= crypto.subtle.importKey('pkcs8', pemToDer(this.account.private_key) as unknown as ArrayBuffer, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
    return this.keyPromise;
  }

  private async fetchToken(): Promise<string> {
    const tokenUri = this.account.token_uri ?? 'https://oauth2.googleapis.com/token';
    const iat = Math.floor(this.now() / 1000);
    const enc = new TextEncoder();
    const header = b64url(enc.encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
    const claims = b64url(enc.encode(JSON.stringify({ iss: this.account.client_email, scope: SCOPE, aud: tokenUri, iat, exp: iat + 3600 })));
    const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', await this.key(), enc.encode(`${header}.${claims}`));
    const res = await this.fetchImpl(tokenUri, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${header}.${claims}.${b64url(signature)}` }),
    });
    if (!res.ok) throw new Error(`Scambio del token Google fallito (${res.status})`);
    const json = (await res.json()) as { access_token: string; expires_in: number };
    this.cached = { token: json.access_token, expiresAt: this.now() + json.expires_in * 1000 };
    return json.access_token;
  }
}
