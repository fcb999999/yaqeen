// بناء ملف «أسطر المصحف» (assets/quran/mushaf_lines.json): ترتيب مصحف المدينة النبوية
// ٦٠٤ صفحات × ١٥ سطرًا، كلمةً كلمةً بنص حفص من مجمع الملك فهد (text_qpc_hafs) المصمَّم لخط حفص العثماني المضمَّن.
// المصدر: api.quran.com v4 (رقم السطر لكل كلمة). يُستعمل للعرض فقط؛ النص المتحقَّق منه في quran.json لا يتغير.
//
// الاستخدام:  node tools/build-mushaf-lines.mjs <quran.json> <out.json> [raw-cache.json]
//   إن وُجد raw-cache.json يُقرأ بدل الجلب من الشبكة (وإن لم يوجد يُجلب ويُحفظ فيه).
//
// صيغة الناتج: pages[p-1] = 15 سطرًا، كل سطر واحد من:
//   "H<رقم السورة>"  ترويسة سورة
//   "B"              بسملة
//   [[ayahId, "كلمة كلمة ... ٦"], ...]  مقاطع الآيات في السطر بالترتيب (رقم الآية آخر رمز في مقطعها)
//   ""               سطر فارغ (لا يُتوقع؛ يُبلَّغ عنه)
// أي فشل في التحقق يوقف البناء.
import fs from 'node:fs';

const [quranPath, outPath, cachePath] = process.argv.slice(2);
if (!quranPath || !outPath) { console.error('usage: build-mushaf-lines.mjs <quran.json> <out.json> [raw-cache.json]'); process.exit(2); }
const fail = (m) => { console.error('FAIL: ' + m); process.exit(1); };

async function fetchPage(p) {
  const url = `https://api.quran.com/api/v4/verses/by_page/${p}?words=true&word_fields=text_qpc_hafs,line_number,char_type_name&per_page=100&fields=text_qpc_hafs`;
  for (let i = 0; i < 5; i++) {
    try { const r = await fetch(url); if (r.ok) return await r.json(); } catch {}
    await new Promise((r) => setTimeout(r, 1000 * (i + 1)));
  }
  throw new Error('fetch failed: page ' + p);
}

let raw;
if (cachePath && fs.existsSync(cachePath)) {
  raw = JSON.parse(fs.readFileSync(cachePath, 'utf8'));
} else {
  raw = {};
  const queue = Array.from({ length: 604 }, (_, i) => i + 1);
  await Promise.all(Array.from({ length: 8 }, async () => {
    while (queue.length) {
      const p = queue.shift();
      const j = await fetchPage(p);
      if (j.pagination && j.pagination.total_pages > 1) fail('paged response for page ' + p);
      raw[p] = j.verses.map((v) => ({ k: v.verse_key, w: v.words.map((w) => [w.line_number, w.char_type_name, w.text_qpc_hafs]) }));
    }
  }));
  if (cachePath) fs.writeFileSync(cachePath, JSON.stringify(raw));
}

const quran = JSON.parse(fs.readFileSync(quranPath, 'utf8'));
// صف الآية في quran.json: [surah, number, text, page, juz, hizbQuarter?, ...] ومعرّفها = ترتيبها (1..6236)
const ayahId = new Map();
const ayahPage = new Map();
quran.ayahs.forEach((a, i) => { ayahId.set(`${a[0]}:${a[1]}`, i + 1); ayahPage.set(`${a[0]}:${a[1]}`, a[3]); });
if (ayahId.size !== 6236) fail('quran.json ayah count ' + ayahId.size);

const DIGITS = /^[٠-٩]+$/;
const pages = [];
const seen = new Set();
const report = { headers: 0, bismillahs: 0, emptyLines: [] };
let wordCount = 0;

for (let p = 1; p <= 604; p++) {
  const verses = raw[p];
  if (!verses || !verses.length) fail('no verses on page ' + p);
  const lines = Array.from({ length: 15 }, () => null);
  for (const v of verses) {
    if (seen.has(v.k)) fail('verse on two pages ' + v.k);
    seen.add(v.k);
    const id = ayahId.get(v.k);
    if (!id) fail('unknown verse ' + v.k);
    if (ayahPage.get(v.k) !== p) fail(`page mismatch ${v.k}: quran.com ${p} vs quran.json ${ayahPage.get(v.k)}`);
    const ends = v.w.filter((w) => w[1] === 'end');
    if (ends.length !== 1 || v.w[v.w.length - 1][1] !== 'end') fail('verse end marker ' + v.k);
    if (!DIGITS.test(ends[0][2])) fail('end marker not digits ' + v.k + ' ' + ends[0][2]);
    for (const [line, type, text] of v.w) {
      if (!(line >= 1 && line <= 15)) fail(`line ${line} on page ${p}`);
      // قد تحمل «الكلمة» مسافة داخلية (علامة الحزب «۞ إِنَّ»، أو «بَعۡدَ مَا»): تصير رموزًا مستقلة في السطر
      if (!text || /^\s|\s$|\s\s|[\t\n\r]/.test(text)) fail(`bad word text "${text}" ${v.k}`);
      if (type !== 'word' && type !== 'end') fail('word type ' + type);
      const l = (lines[line - 1] ??= []);
      const last = l[l.length - 1];
      if (last && last[0] === id) last[1] += ' ' + text; else l.push([id, text]);
      wordCount++;
    }
  }
  // ترويسات السور والبسملة في الأسطر التي تسبق أول آية من كل سورة تبدأ في هذه الصفحة
  for (const v of verses) {
    const [s, n] = v.k.split(':').map(Number);
    if (n !== 1) continue;
    const first = v.w[0][0];
    const needB = s !== 1 && s !== 9;
    let at = first - 1;
    if (needB) {
      if (at < 1 || lines[at - 1] !== null) fail(`no bismillah slot for surah ${s} on page ${p}`);
      lines[at - 1] = 'B'; report.bismillahs++; at--;
    }
    if (at >= 1) {
      if (lines[at - 1] !== null) fail(`no header slot for surah ${s} on page ${p}`);
      lines[at - 1] = 'H' + s; report.headers++;
    } else {
      // الترويسة في آخر سطر من الصفحة السابقة (يقع هذا في مصحف المدينة)
      const prev = pages[p - 2];
      if (!prev || prev[14] !== '') fail(`header of surah ${s} expected at end of page ${p - 1}`);
      prev[14] = 'H' + s; report.headers++;
    }
  }
  for (let i = 0; i < 15; i++) if (lines[i] === null) lines[i] = '';
  pages.push(lines);
}

if (seen.size !== 6236) fail('verse count ' + seen.size);
if (report.headers !== 114) fail('header count ' + report.headers);
if (report.bismillahs !== 112) fail('bismillah count ' + report.bismillahs);
// الصفحتان الأولى والثانية في المصحف أسطرهما أقل (٨) وتتوسطان الصفحة؛ بقية الصفحات يجب أن تكتمل
for (let p = 3; p <= 604; p++) pages[p - 1].forEach((l, i) => { if (l === '') report.emptyLines.push(`${p}:${i + 1}`); });
if (report.emptyLines.length) fail('unexplained empty lines: ' + report.emptyLines.join(' '));
for (const p of [1, 2]) while (pages[p - 1].length && pages[p - 1][pages[p - 1].length - 1] === '') pages[p - 1].pop();

const out = {
  format: 1,
  source: 'api.quran.com v4 (Madani mushaf line layout, text_qpc_hafs by King Fahd Glorious Quran Printing Complex) — display layout only',
  pages,
};
fs.writeFileSync(outPath, JSON.stringify(out));
console.log(`OK pages=${pages.length} verses=${seen.size} words=${wordCount} headers=${report.headers} bismillahs=${report.bismillahs} bytes=${fs.statSync(outPath).size}`);
