/** Errore "previsto" di un tool: il messaggio arriva cosi com'e' al modello. */
export class ToolError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ToolError';
  }
}
