import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import test from 'node:test';
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from 'jose';
import { beijingDay, createService, dayStart, type Env } from '../src/index';

const DAY = 86_400_000;
const id = '43b531bc-96e0-463d-9e2f-86f672a1b88b';
const keys = await generateKeyPair('RS256', { extractable: true });
const publicKey = { ...await exportJWK(keys.publicKey), kid: 'test-key', alg: 'RS256' };

/** Exercise the actual migration and production SQL using SQLite, without remote data. */
function database() {
  const sqlite = new DatabaseSync(':memory:');
  sqlite.exec(readFileSync(new URL('../migrations/0001_usage.sql', import.meta.url), 'utf8'));
  const prepare = (sql: string, params: Array<string | number> = []) => ({
    bind: (...values: Array<string | number>) => prepare(sql, values),
    async run() { sqlite.prepare(sql).run(...params); return { success: true }; },
    async all() { return { success: true, results: sqlite.prepare(sql).all(...params) }; },
  });
  return { sqlite, db: { prepare, batch: (statements: Array<ReturnType<typeof prepare>>) => Promise.all(statements.map(s => s.all())) } };
}

function fixture() {
  const { sqlite, db } = database();
  let time = Date.parse('2026-09-11T04:00:00.000Z');
  const limiter = { limit: async () => ({ success: true }) };
  const env = {
    DB: db, USAGE_HOST: 'usage.hidisiwa.xyz', STATS_HOST: 'stats.hidisiwa.xyz',
    ACCESS_TEAM_DOMAIN: 'unit-test.cloudflareaccess.com', ACCESS_AUD: 'admin-audience', ADMIN_EMAIL: '822069905@qq.com',
    USAGE_IP_LIMITER: limiter, USAGE_INSTALL_LIMITER: limiter,
  } as unknown as Env;
  const service = createService({ now: () => time, jwks: () => createLocalJWKSet({ keys: [publicKey] }) });
  const request = (url: string, init?: RequestInit) => service.fetch(new Request(url, init) as Request, env);
  const usage = (body: unknown = { installationId: id, version: '1.0.73' }) => request('https://usage.hidisiwa.xyz/v1/usage', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'CF-Connecting-IP': '192.0.2.1' }, body: JSON.stringify(body),
  });
  const token = (payload: Record<string, unknown> = {}, key = keys.privateKey) => new SignJWT({ email: env.ADMIN_EMAIL, ...payload })
    .setProtectedHeader({ alg: 'RS256', kid: 'test-key' }).setIssuer('https://' + env.ACCESS_TEAM_DOMAIN)
    .setAudience(env.ACCESS_AUD).setSubject('admin').setIssuedAt(Math.floor(time / 1000))
    .setExpirationTime(Math.floor(time / 1000) + 3600).sign(key);
  const summary = async () => request('https://stats.hidisiwa.xyz/api/summary', { headers: { 'Cf-Access-Jwt-Assertion': await token() } });
  return { sqlite, env, request, usage, token, summary, setTime: (value: number) => { time = value; }, getTime: () => time };
}

test('uses the Beijing midnight boundary including year rollover', () => {
  assert.equal(beijingDay(Date.parse('2026-12-31T15:59:59Z')), '2026-12-31');
  assert.equal(beijingDay(Date.parse('2026-12-31T16:00:00Z')), '2027-01-01');
  assert.equal(dayStart(Date.parse('2026-09-11T04:00:00Z')), Date.parse('2026-09-10T16:00:00Z'));
});

test('empty database returns three zeroes and an explicit starting time', async () => {
  const f = fixture();
  const response = await f.summary();
  assert.equal(response.status, 200);
  const body = await response.json() as any;
  assert.deepEqual([body.totalDevices, body.todayActive, body.last30DaysActive], [0, 0, 0]);
  assert.equal(body.timeZone, 'Asia/Shanghai');
  assert.ok(Number.isFinite(Date.parse(body.startedAt)));
  assert.match(response.headers.get('Cache-Control')!, /no-store/);
});

test('retries and concurrent duplicates count a single installation and retain first use', async () => {
  const f = fixture();
  const first = f.getTime();
  assert.equal((await f.usage()).status, 200);
  f.setTime(first + 1000);
  const duplicates = await Promise.all(Array.from({ length: 8 }, () => f.usage({ installationId: id.toUpperCase(), version: '1.0.74' })));
  assert.ok(duplicates.every(r => r.status === 200));
  const row = f.sqlite.prepare('SELECT * FROM installations').get()!;
  assert.equal(row.first_seen_at, first);
  assert.equal(row.last_seen_at, first + 1000);
  assert.equal(row.app_version, '1.0.74');
  const counts = await (await f.summary()).json() as any;
  assert.deepEqual([counts.totalDevices, counts.todayActive, counts.last30DaysActive], [1, 1, 1]);
});

test('a new Beijing day becomes active only after another successful report', async () => {
  const f = fixture();
  f.setTime(Date.parse('2026-09-11T15:59:59Z'));
  assert.equal((await (await f.usage()).json() as any).day, '2026-09-11');
  f.setTime(Date.parse('2026-09-11T16:00:00Z'));
  assert.equal((await (await f.summary()).json() as any).todayActive, 0);
  assert.equal((await (await f.usage()).json() as any).day, '2026-09-12');
  const counts = await (await f.summary()).json() as any;
  assert.deepEqual([counts.totalDevices, counts.todayActive, counts.last30DaysActive], [1, 1, 1]);
});

test('30 days includes today and exactly the preceding 29 calendar days', async () => {
  const f = fixture();
  const insert = f.sqlite.prepare('INSERT INTO installations VALUES (?, ?, ?, ?)');
  for (const [suffix, time] of [['a', f.getTime()], ['b', dayStart(f.getTime()) - 29 * DAY], ['c', dayStart(f.getTime()) - 29 * DAY - 1]] as const) {
    insert.run(suffix, time, time, '1.0.73');
  }
  const counts = await (await f.summary()).json() as any;
  assert.deepEqual([counts.totalDevices, counts.todayActive, counts.last30DaysActive], [3, 1, 2]);
});

test('invalid or extra fields and unbounded request bodies never write records', async () => {
  const f = fixture();
  for (const value of [null, [], {}, { installationId: 'phone-number', version: '1.0.73' },
    { installationId: id, version: 'x'.repeat(5000) }, { installationId: id, version: '1.0.73', studentId: 'private' },
    { installationId: id, version: '<script>' }]) assert.equal((await f.usage(value)).status, 400);
  assert.equal((await f.request('https://usage.hidisiwa.xyz/v1/usage', { method: 'POST', body: 'not json' })).status, 400);
  const stream = new ReadableStream({ start(controller) { controller.enqueue(new Uint8Array(513).fill(32)); controller.close(); } });
  assert.equal((await f.request('https://usage.hidisiwa.xyz/v1/usage', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: stream, duplex: 'half',
  } as RequestInit)).status, 400);
  assert.equal(f.sqlite.prepare('SELECT COUNT(*) AS n FROM installations').get()!.n, 0);
});

test('both IP and installation limits prevent writes and return retry information', async () => {
  const f = fixture();
  f.env.USAGE_IP_LIMITER = { limit: async () => ({ success: false }) };
  let response = await f.usage();
  assert.equal(response.status, 429);
  assert.equal(response.headers.get('Retry-After'), '60');
  f.env.USAGE_IP_LIMITER = { limit: async () => ({ success: true }) };
  f.env.USAGE_INSTALL_LIMITER = { limit: async () => ({ success: false }) };
  response = await f.usage();
  assert.equal(response.status, 429);
  assert.equal(f.sqlite.prepare('SELECT COUNT(*) AS n FROM installations').get()!.n, 0);
});

test('the page and summary reject absent, forged and other-email identities', async () => {
  const f = fixture();
  const otherKey = await generateKeyPair('RS256');
  for (const path of ['/', '/api/summary']) {
    assert.equal((await f.request('https://stats.hidisiwa.xyz' + path)).status, 401);
    for (const token of ['garbage', await f.token({ email: 'someone@example.com' }), await f.token({}, otherKey.privateKey)]) {
      const response = await f.request('https://stats.hidisiwa.xyz' + path, {
        headers: { 'Cf-Access-Jwt-Assertion': token, 'Cf-Access-Authenticated-User-Email': f.env.ADMIN_EMAIL },
      });
      assert.equal(response.status, 401);
      assert.equal((await response.json() as any).error, 'unauthorized');
    }
  }
});

test('valid signatures still require the right audience, issuer and expiry', async () => {
  const f = fixture();
  const original = await f.token();
  f.env.ACCESS_AUD = 'another-app';
  assert.equal((await f.request('https://stats.hidisiwa.xyz/api/summary', { headers: { 'Cf-Access-Jwt-Assertion': original } })).status, 401);
  f.env.ACCESS_AUD = 'admin-audience';
  f.env.ACCESS_TEAM_DOMAIN = 'another-team.cloudflareaccess.com';
  assert.equal((await f.request('https://stats.hidisiwa.xyz/api/summary', { headers: { 'Cf-Access-Jwt-Assertion': original } })).status, 401);
  f.env.ACCESS_TEAM_DOMAIN = 'unit-test.cloudflareaccess.com';
  f.setTime(f.getTime() + 3_601_000);
  assert.equal((await f.request('https://stats.hidisiwa.xyz/api/summary', { headers: { 'Cf-Access-Jwt-Assertion': original } })).status, 401);
});

test('alternate hosts, previews, public read paths and incorrect methods cannot expose statistics', async () => {
  const f = fixture();
  const headers = { 'Cf-Access-Jwt-Assertion': await f.token() };
  for (const host of ['academic-assistant-usage.822069905.workers.dev', 'preview.example.org', 'usage.hidisiwa.xyz']) {
    assert.equal((await f.request('https://' + host + '/api/summary', { headers })).status, 404);
  }
  assert.equal((await f.request('https://usage.hidisiwa.xyz/v1/usage')).status, 405);
  assert.equal((await f.request('https://stats.hidisiwa.xyz/api/summary', { method: 'POST', headers })).status, 405);
  f.env.ACCESS_AUD = '';
  assert.equal((await f.request('https://stats.hidisiwa.xyz/', { headers })).status, 401);
});

test('authorized dashboard has a restrictive nonce policy and no external assets', async () => {
  const f = fixture();
  const response = await f.request('https://stats.hidisiwa.xyz/', { headers: { 'Cf-Access-Jwt-Assertion': await f.token() } });
  assert.equal(response.status, 200);
  const html = await response.text();
  const nonce = /<script nonce="([^"]+)"/.exec(html)![1];
  assert.ok(response.headers.get('Content-Security-Policy')!.includes("script-src 'nonce-" + nonce + "'"));
  assert.equal(response.headers.get('X-Frame-Options'), 'DENY');
  assert.match(html, /不代表实际人数/);
  assert.doesNotMatch(html, /<script[^>]+src=|<link[^>]+href=/);
});

test('database failure never acknowledges an unsaved report', async () => {
  const f = fixture();
  f.env.DB = { prepare() { throw new Error('offline'); } } as any;
  const response = await f.usage();
  assert.equal(response.status, 503);
  assert.equal((await response.json() as any).accepted, undefined);
});
