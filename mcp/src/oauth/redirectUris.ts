/**
 * Redirect URI ammessi. Solo quelli di Claude (app web, desktop, mobile) e,
 * se abilitato, i loopback (Claude Code, MCP Inspector, test locali).
 * Mai altro: il codice di autorizzazione viaggia sul redirect.
 */
export const CLAUDE_REDIRECT_URIS: readonly string[] = [
  'https://claude.ai/api/mcp/auth_callback',
  'https://claude.com/api/mcp/auth_callback',
];

const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

export function isAllowedRedirectUri(uri: string, options: { allowLoopback: boolean }): boolean {
  if (CLAUDE_REDIRECT_URIS.includes(uri)) return true;
  if (!options.allowLoopback) return false;
  let url: URL;
  try {
    url = new URL(uri);
  } catch {
    return false;
  }
  return url.protocol === 'http:' && LOOPBACK_HOSTS.has(url.hostname) && !url.hash && !url.username && !url.password;
}
