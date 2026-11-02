/**
 * Stato della frequenza dei check-in, portato da core/domain (CheckInThreshold, CheckInStatus,
 * GetUrgentCheckInsUseCase).
 */

export interface Threshold {
  warningDays: number;
  criticalDays: number;
}

/** Default globale dell'app: avviso dopo due settimane, critico dopo un mese. */
export const DEFAULT_THRESHOLD: Threshold = { warningDays: 14, criticalDays: 30 };

export type CheckInStatus = 'FRESH' | 'DUE' | 'OVERDUE' | 'NEVER';

/** Stato dai giorni passati dall'ultimo check-in (null = mai visto). */
export function checkInStatus(daysSince: number | null, threshold: Threshold): CheckInStatus {
  if (daysSince === null) return 'NEVER';
  if (daysSince < threshold.warningDays) return 'FRESH';
  if (daysSince < threshold.criticalDays) return 'DUE';
  return 'OVERDUE';
}

export function urgencyRank(status: CheckInStatus): number {
  return { NEVER: 3, OVERDUE: 2, DUE: 1, FRESH: 0 }[status];
}

/** Soglia della persona solo se ha entrambi i valori (thresholdOf nei mapper), altrimenti quella globale. */
export function thresholdFor(person: Record<string, unknown>, global: Threshold): Threshold {
  if (typeof person.warningDays === 'number' && typeof person.criticalDays === 'number') {
    return { warningDays: person.warningDays, criticalDays: person.criticalDays };
  }
  return global;
}

/** Soglie globali valide come UpdateDefaultThresholdUseCase: warning >= 1, critical >= warning + 1. */
export function coerceGlobalThreshold(warningDays: number, criticalDays: number): Threshold {
  const warning = Math.max(1, Math.trunc(warningDays));
  return { warningDays: warning, criticalDays: Math.max(warning + 1, Math.trunc(criticalDays)) };
}

/** Ordinamento della sezione "check-in urgenti": stato piu urgente, poi piu giorni (mai visto = infinito). */
export function compareUrgency(a: { status: CheckInStatus; daysSince: number | null }, b: { status: CheckInStatus; daysSince: number | null }): number {
  const rank = urgencyRank(b.status) - urgencyRank(a.status);
  if (rank !== 0) return rank;
  return (b.daysSince ?? Number.MAX_SAFE_INTEGER) - (a.daysSince ?? Number.MAX_SAFE_INTEGER);
}
