#!/usr/bin/env node
// يُنفَّذ في مرحلة الإصدار (GitHub Actions) بعد نشر الـAPK:
//   1) يكتب وثيقة Firestore:  app_versions/<versionName>
//   2) يرسل رسالة FCM (data فقط) إلى الموضوع "app_updates"
//
// الاستخدام:
//   node tools/notify-release.mjs --version 1.2.0 --version-code 12 --apk-url https://... --sha256 <hex> \
//        --changelog-file notes.md [--release-url https://...] [--published-at 2026-11-04T10:00:00Z] [--dry-run]
//   node tools/notify-release.mjs --print-changelog 1.2.0 [--changelog-source CHANGELOG.md]
//
// الاعتماد: متغير البيئة FIREBASE_SERVICE_ACCOUNT_JSON (محتوى ملف حساب الخدمة). لا يُقرأ ولا يُطبع في وضع --dry-run.
// حزمة firebase-admin (tools/package.json) تُحمَّل عند الحاجة فقط، فيعمل --dry-run بدونها.

import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export const TOPIC = 'app_updates';
export const COLLECTION = 'app_versions';
const SEMVER = /^v?(\d+(?:\.\d+){0,3})(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$/;

/** يزيل بادئة v ويتحقق من صحة رقم الإصدار. */
export function normalizeVersion(raw) {
  const v = String(raw ?? '').trim();
  const m = SEMVER.exec(v);
  if (!m) throw new Error(`رقم إصدار غير صالح: "${raw}"`);
  return v.replace(/^v/i, '');
}

/** يستخرج قسم الإصدار من CHANGELOG.md (عنوان "## [1.2.0]" أو "## 1.2.0" أو "## v1.2.0 - تاريخ"). */
export function extractChangelog(markdown, version) {
  const wanted = normalizeVersion(version);
  const lines = String(markdown ?? '').replace(/\r\n?/g, '\n').split('\n');
  const heading = /^##\s+\[?v?([0-9][^\]\s]*)\]?(?:\s.*)?$/;
  let start = -1;
  for (let i = 0; i < lines.length; i++) {
    const m = heading.exec(lines[i]);
    if (m && m[1] === wanted) { start = i + 1; break; }
  }
  if (start < 0) return '';
  let end = lines.length;
  for (let i = start; i < lines.length; i++) {
    if (/^##\s/.test(lines[i]) && !/^###/.test(lines[i])) { end = i; break; }
  }
  return lines.slice(start, end).join('\n').trim();
}

/**
 * يبني الحمولتين اللتين تُرسلان فعليًا (دالة خالصة بلا شبكة). كل قيم data في FCM نصوص.
 * publishedAt بصيغة ISO-8601 (يحوّله الكاتب إلى Timestamp في Firestore).
 */
export function buildPayloads({ version, versionCode, apkUrl, sha256 = '', changelog = '', releaseUrl = '', publishedAt }) {
  const versionName = normalizeVersion(version);
  const code = Number(versionCode);
  if (!Number.isInteger(code) || code < 1) throw new Error(`versionCode غير صالح: "${versionCode}"`);
  if (!/^https:\/\//.test(String(apkUrl ?? ''))) throw new Error('apkUrl يجب أن يكون رابط https');
  if (sha256 && !/^[0-9a-fA-F]{64}$/.test(sha256)) throw new Error('sha256 يجب أن يكون 64 خانة سداسية');
  if (releaseUrl && !/^https:\/\//.test(releaseUrl)) throw new Error('releaseUrl يجب أن يكون رابط https');
  const iso = publishedAt ? new Date(publishedAt).toISOString() : new Date().toISOString();

  const doc = {
    versionName,
    versionCode: code,
    publishedAt: iso,
    changelog: String(changelog).trim(),
    apkUrl,
    sha256: sha256.toLowerCase(),
  };
  const message = {
    topic: TOPIC,
    data: { type: 'update', version: versionName, ...(releaseUrl ? { url: releaseUrl } : {}) },
    android: { priority: 'high', ttl: 7 * 24 * 3600 * 1000 },
  };
  return { docPath: `${COLLECTION}/${versionName}`, doc, message };
}

export function parseArgs(argv) {
  const out = { flags: new Set(), values: {} };
  const valued = new Set(['version', 'version-code', 'apk-url', 'sha256', 'changelog-file', 'changelog', 'release-url', 'published-at', 'print-changelog', 'changelog-source']);
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith('--')) throw new Error(`وسيط غير متوقع: ${a}`);
    const key = a.slice(2);
    if (valued.has(key)) {
      const v = argv[++i];
      if (v === undefined) throw new Error(`القيمة مفقودة للوسيط --${key}`);
      out.values[key] = v;
    } else if (key === 'dry-run' || key === 'help') {
      out.flags.add(key);
    } else {
      throw new Error(`وسيط غير معروف: --${key}`);
    }
  }
  return out;
}

async function send({ docPath, doc, message }) {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) throw new Error('المتغير FIREBASE_SERVICE_ACCOUNT_JSON غير مضبوط (استخدم --dry-run للمعاينة بلا اعتماد).');
  let credentialJson;
  try { credentialJson = JSON.parse(raw); } catch { throw new Error('FIREBASE_SERVICE_ACCOUNT_JSON ليس JSON صالحًا.'); }
  const admin = (await import('firebase-admin')).default;
  admin.initializeApp({ credential: admin.credential.cert(credentialJson) });
  const db = admin.firestore();
  await db.doc(docPath).set({ ...doc, publishedAt: admin.firestore.Timestamp.fromDate(new Date(doc.publishedAt)) });
  console.log(`تمت كتابة ${docPath}`);
  const id = await admin.messaging().send(message);
  console.log(`أُرسلت رسالة FCM إلى الموضوع ${message.topic}: ${id}`);
}

export async function main(argv, env = process.env) {
  const { flags, values } = parseArgs(argv);
  if (flags.has('help')) {
    console.log('انظر التعليق في أعلى tools/notify-release.mjs');
    return 0;
  }
  if (values['print-changelog']) {
    const src = values['changelog-source'] ?? 'CHANGELOG.md';
    let md = '';
    try { md = readFileSync(src, 'utf8'); } catch { /* لا ملف: نص افتراضي أدناه */ }
    const section = extractChangelog(md, values['print-changelog']);
    process.stdout.write((section || `إصدار ${normalizeVersion(values['print-changelog'])} من يقين.`) + '\n');
    return 0;
  }
  let changelog = values.changelog ?? '';
  if (values['changelog-file']) changelog = readFileSync(values['changelog-file'], 'utf8');
  const payloads = buildPayloads({
    version: values.version,
    versionCode: values['version-code'],
    apkUrl: values['apk-url'],
    sha256: values.sha256 ?? '',
    changelog,
    releaseUrl: values['release-url'] ?? '',
    publishedAt: values['published-at'],
  });
  if (flags.has('dry-run')) {
    console.log('--dry-run: لن يُكتب شيء ولن تُرسل أي رسالة.\n');
    console.log(`Firestore  ${payloads.docPath}`);
    console.log(JSON.stringify(payloads.doc, null, 2));
    console.log('\nFCM');
    console.log(JSON.stringify(payloads.message, null, 2));
    return 0;
  }
  await send(payloads);
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2)).then((c) => process.exit(c), (e) => { console.error(`خطأ: ${e.message}`); process.exit(1); });
}
