// اختبارات: node --test tools/notify-release.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { buildPayloads, extractChangelog, normalizeVersion, parseArgs, TOPIC } from './notify-release.mjs';

const HEX = 'ab'.repeat(32);
const base = { version: 'v1.2.0', versionCode: '12', apkUrl: 'https://github.com/o/r/releases/download/v1.2.0/app-release.apk', sha256: HEX.toUpperCase(), changelog: '- ميزة\n', releaseUrl: 'https://github.com/o/r/releases/tag/v1.2.0', publishedAt: '2026-11-04T10:00:00Z' };

test('normalizeVersion', () => {
  assert.equal(normalizeVersion('v1.2.3'), '1.2.3');
  assert.equal(normalizeVersion('2.0.0-beta.1'), '2.0.0-beta.1');
  assert.throws(() => normalizeVersion('latest'));
  assert.throws(() => normalizeVersion(''));
});

test('buildPayloads: وثيقة Firestore', () => {
  const { docPath, doc } = buildPayloads(base);
  assert.equal(docPath, 'app_versions/1.2.0');
  assert.deepEqual(doc, {
    versionName: '1.2.0',
    versionCode: 12,
    publishedAt: '2026-11-04T10:00:00.000Z',
    changelog: '- ميزة',
    apkUrl: base.apkUrl,
    sha256: HEX,
  });
});

test('buildPayloads: رسالة FCM بيانات فقط وكل القيم نصوص', () => {
  const { message } = buildPayloads(base);
  assert.equal(message.topic, TOPIC);
  assert.equal(message.topic, 'app_updates');
  assert.equal(message.notification, undefined);
  assert.deepEqual(message.data, { type: 'update', version: '1.2.0', url: base.releaseUrl });
  for (const v of Object.values(message.data)) assert.equal(typeof v, 'string');
  const noUrl = buildPayloads({ ...base, releaseUrl: '' }).message;
  assert.deepEqual(noUrl.data, { type: 'update', version: '1.2.0' });
});

test('buildPayloads: يرفض المدخلات الخاطئة', () => {
  assert.throws(() => buildPayloads({ ...base, version: 'x' }));
  assert.throws(() => buildPayloads({ ...base, versionCode: '0' }));
  assert.throws(() => buildPayloads({ ...base, versionCode: 'abc' }));
  assert.throws(() => buildPayloads({ ...base, apkUrl: 'http://insecure/app.apk' }));
  assert.throws(() => buildPayloads({ ...base, sha256: 'zz' }));
  assert.throws(() => buildPayloads({ ...base, releaseUrl: 'ftp://x' }));
});

test('extractChangelog', () => {
  const md = '# سجل التغييرات\n\n## [1.2.0] - 2026-11-04\n\n### الجديد\n- أ\n- ب\n\n## [1.1.0] - 2026-10-01\n- قديم\n';
  assert.equal(extractChangelog(md, 'v1.2.0'), '### الجديد\n- أ\n- ب');
  assert.equal(extractChangelog(md, '1.1.0'), '- قديم');
  assert.equal(extractChangelog(md, '9.9.9'), '');
  assert.equal(extractChangelog('## 1.0.0\r\nنص', '1.0.0'), 'نص');
  assert.equal(extractChangelog('', '1.0.0'), '');
});

test('parseArgs', () => {
  const a = parseArgs(['--version', '1.0.0', '--dry-run']);
  assert.equal(a.values.version, '1.0.0');
  assert.ok(a.flags.has('dry-run'));
  assert.throws(() => parseArgs(['--version']));
  assert.throws(() => parseArgs(['--nope']));
  assert.throws(() => parseArgs(['positional']));
});

test('CLI --dry-run يطبع الحمولتين بلا اعتماد', () => {
  const script = fileURLToPath(new URL('./notify-release.mjs', import.meta.url));
  const env = { ...process.env };
  delete env.FIREBASE_SERVICE_ACCOUNT_JSON;
  const out = execFileSync(process.execPath, [script, '--dry-run', '--version', 'v1.2.0', '--version-code', '12', '--apk-url', base.apkUrl, '--sha256', HEX, '--changelog', 'x', '--published-at', '2026-11-04T10:00:00Z'], { env, encoding: 'utf8' });
  assert.match(out, /app_versions\/1\.2\.0/);
  assert.match(out, /"topic": "app_updates"/);
  assert.match(out, /"type": "update"/);
});

test('CLI بلا اعتماد وبلا --dry-run يفشل برسالة واضحة', () => {
  const script = fileURLToPath(new URL('./notify-release.mjs', import.meta.url));
  const env = { ...process.env };
  delete env.FIREBASE_SERVICE_ACCOUNT_JSON;
  assert.throws(() => execFileSync(process.execPath, [script, '--version', '1.0.0', '--version-code', '1', '--apk-url', base.apkUrl], { env, encoding: 'utf8', stdio: 'pipe' }), /FIREBASE_SERVICE_ACCOUNT_JSON/);
});
