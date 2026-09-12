import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from 'jose';
import { renderDashboard } from './page';

export interface Env {
  DB: D1Database;
  USAGE_HOST: string;
  STATS_HOST: string;
  ACCESS_TEAM_DOMAIN: string;
  ACCESS_AUD: string;
  ADMIN_EMAIL: string;
  USAGE_IP_LIMITER: { limit(input: { key: string }): Promise<{ success: boolean }> };
  USAGE_INSTALL_LIMITER: { limit(input: { key: string }): Promise<{ success: boolean }> };
}

const DAY = 86_400_000;
const CHINA_OFFSET = 8 * 3_600_000;
const MAX_BODY_BYTES = 512;
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const VERSION = /^\d{1,4}(?:\.\d{1,6}){1,3}(?:-[a-zA-Z0-9.-]{1,24})?$/;

export function beijingDay(now: number): string {
  return new Date(now + CHINA_OFFSET).toISOString().slice(0, 10);
}

export function dayStart(now: number): number {
  return Math.floor((now + CHINA_OFFSET) / DAY) * DAY - CHINA_OFFSET;
}

function json(body: unknown, status = 200, headers: HeadersInit = {}): Response {
  return Response.json(body, { status, headers });
}

function secure(response: Response): Response {
  response.headers.set('Cache-Control', 'private, no-store');
  response.headers.set('X-Content-Type-Options', 'nosniff');
  response.headers.set('Referrer-Policy', 'no-referrer');
  response.headers.set('X-Robots-Tag', 'noindex, nofollow');
  response.headers.set('X-Frame-Options', 'DENY');
  return response;
}

async function readUsage(request: Request): Promise<{ installationId: string; version: string } | null> {
  if (request.headers.get('content-type')?.split(';', 1)[0].trim().toLowerCase() !== 'application/json') return null;
  const length = request.headers.get('content-length');
  if (length !== null && (!/^\d+$/.test(length) || Number(length) > MAX_BODY_BYTES)) return null;
  const reader = request.body?.getReader();
  if (!reader) return null;
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    for (;;) {
      const chunk = await reader.read();
      if (chunk.done) break;
      size += chunk.value.byteLength;
      if (size > MAX_BODY_BYTES) { await reader.cancel(); return null; }
      chunks.push(chunk.value);
    }
    const body = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.byteLength; }
    const value: unknown = JSON.parse(new TextDecoder('utf-8', { fatal: true, ignoreBOM: false }).decode(body));
    if (value === null || typeof value !== 'object' || Array.isArray(value)) return null;
    const data = value as Record<string, unknown>;
    if (Object.keys(data).length !== 2 || typeof data.installationId !== 'string' || typeof data.version !== 'string') return null;
    if (!UUID_V4.test(data.installationId) || !VERSION.test(data.version)) return null;
    return { installationId: data.installationId.toLowerCase(), version: data.version };
  } catch { return null; }
  finally { reader.releaseLock(); }
}

export function createService(options: { now?: () => number; jwks?: (url: URL) => JWTVerifyGetKey } = {}) {
  const now = options.now ?? Date.now;
  const resolvers = new Map<string, JWTVerifyGetKey>();

  async function authorized(request: Request, env: Env): Promise<boolean> {
    if (!/^[a-z0-9-]+\.cloudflareaccess\.com$/.test(env.ACCESS_TEAM_DOMAIN) || !env.ACCESS_AUD || !env.ADMIN_EMAIL) return false;
    const token = request.headers.get('Cf-Access-Jwt-Assertion');
    if (!token || token.length > 16_384) return false;
    const issuer = 'https://' + env.ACCESS_TEAM_DOMAIN;
    let resolver = resolvers.get(issuer);
    if (!resolver) {
      const url = new URL(issuer + '/cdn-cgi/access/certs');
      resolver = options.jwks ? options.jwks(url) : createRemoteJWKSet(url);
      resolvers.set(issuer, resolver);
    }
    try {
      const { payload } = await jwtVerify(token, resolver, {
        issuer, audience: env.ACCESS_AUD, algorithms: ['RS256'],
        requiredClaims: ['iss', 'aud', 'exp', 'iat', 'sub', 'email'],
        currentDate: new Date(now()),
      });
      return typeof payload.email === 'string' && payload.email.toLowerCase() === env.ADMIN_EMAIL.toLowerCase();
    } catch { return false; }
  }

  async function route(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.hostname === env.USAGE_HOST) {
      if (url.pathname !== '/v1/usage') return json({ error: 'not_found' }, 404);
      if (request.method !== 'POST') return json({ error: 'method_not_allowed' }, 405, { Allow: 'POST' });
      const ipLimit = await env.USAGE_IP_LIMITER.limit({ key: request.headers.get('CF-Connecting-IP') ?? 'unknown' });
      if (!ipLimit.success) return json({ error: 'rate_limited' }, 429, { 'Retry-After': '60' });
      const usage = await readUsage(request);
      if (!usage) return json({ error: 'invalid_usage' }, 400);
      const installLimit = await env.USAGE_INSTALL_LIMITER.limit({ key: usage.installationId });
      if (!installLimit.success) return json({ error: 'rate_limited' }, 429, { 'Retry-After': '60' });
      const timestamp = now();
      await env.DB.prepare(`
        INSERT INTO installations(installation_id, first_seen_at, last_seen_at, app_version)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(installation_id) DO UPDATE SET
          first_seen_at = MIN(installations.first_seen_at, excluded.first_seen_at),
          app_version = CASE WHEN excluded.last_seen_at >= installations.last_seen_at
            THEN excluded.app_version ELSE installations.app_version END,
          last_seen_at = MAX(installations.last_seen_at, excluded.last_seen_at)
      `).bind(usage.installationId, timestamp, timestamp, usage.version).run();
      return json({ accepted: true, day: beijingDay(timestamp) });
    }

    // Also deny workers.dev, previews and alternate Host headers at the application layer.
    if (url.hostname !== env.STATS_HOST) return json({ error: 'not_found' }, 404);
    if (!await authorized(request, env)) return json({ error: 'unauthorized' }, 401);
    if (request.method !== 'GET') return json({ error: 'method_not_allowed' }, 405, { Allow: 'GET' });
    if (url.pathname === '/api/summary') {
      const timestamp = now();
      const start = dayStart(timestamp);
      const rows = await env.DB.batch([
        env.DB.prepare(`SELECT COUNT(*) AS totalDevices,
          COALESCE(SUM(last_seen_at >= ? AND last_seen_at <= ?), 0) AS todayActive,
          COALESCE(SUM(last_seen_at >= ? AND last_seen_at <= ?), 0) AS last30DaysActive
          FROM installations`).bind(start, timestamp, start - 29 * DAY, timestamp),
        env.DB.prepare("SELECT value FROM service_metadata WHERE key = 'started_at'"),
      ]);
      const counts = rows[0].results[0];
      const startedAt = Number((rows[1].results[0] as { value?: string } | undefined)?.value);
      if (!counts || !Number.isFinite(startedAt)) throw new Error('Usage schema is not initialized');
      return json({ ...counts, startedAt: new Date(startedAt).toISOString(),
        updatedAt: new Date(timestamp).toISOString(), day: beijingDay(timestamp), timeZone: 'Asia/Shanghai' });
    }
    if (url.pathname === '/') {
      const nonce = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(18))));
      return new Response(renderDashboard(nonce), { headers: {
        'Content-Type': 'text/html; charset=utf-8',
        'Content-Security-Policy': "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'; connect-src 'self'; script-src 'nonce-" + nonce + "'; style-src 'nonce-" + nonce + "'",
      } });
    }
    return json({ error: 'not_found' }, 404);
  }

  return {
    async fetch(request: Request, env: Env): Promise<Response> {
      try { return secure(await route(request, env)); }
      catch { return secure(json({ error: 'temporarily_unavailable' }, 503, { 'Retry-After': '60' })); }
    },
  };
}

export default createService();
