// Sostituto di "cloudflare:workers" per far girare in Node la libreria OAuth (server sulla VPS e test).
export class WorkerEntrypoint {
  constructor(
    public ctx?: unknown,
    public env?: unknown
  ) {}
}
export class DurableObject {}
export class RpcTarget {}
export const env = {};
