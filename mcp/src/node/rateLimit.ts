/**
 * Limite di richieste in memoria con la stessa interfaccia del binding RATE_LIMIT di Workers:
 * finestra fissa, `limit` richieste ogni `periodSec` secondi per chiave (percorso|IP).
 * Un solo processo sulla VPS, quindi la memoria del processo basta.
 */
export class MemoryRateLimiter {
  private readonly windows = new Map<string, { count: number; resetAt: number }>();
  private readonly timer: ReturnType<typeof setInterval>;

  constructor(
    private readonly limitPerWindow = 60,
    private readonly periodSec = 60,
    private readonly nowMs: () => number = Date.now
  ) {
    this.timer = setInterval(() => this.sweep(), periodSec * 1000);
    this.timer.unref();
  }

  /** `retryAfterSec`: secondi alla fine della finestra (in piu rispetto al binding di Workers). */
  async limit({ key }: { key: string }): Promise<{ success: boolean; retryAfterSec: number }> {
    const now = this.nowMs();
    let w = this.windows.get(key);
    if (!w || w.resetAt <= now) {
      w = { count: 0, resetAt: now + this.periodSec * 1000 };
      this.windows.set(key, w);
    }
    w.count++;
    return { success: w.count <= this.limitPerWindow, retryAfterSec: Math.max(1, Math.ceil((w.resetAt - now) / 1000)) };
  }

  private sweep(): void {
    const now = this.nowMs();
    for (const [k, w] of this.windows) if (w.resetAt <= now) this.windows.delete(k);
  }

  close(): void {
    clearInterval(this.timer);
  }
}
