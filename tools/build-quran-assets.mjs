// بناء ملفات القرآن والتفسير المحلية وفحص سلامتها.
// الاستخدام:  node tools/build-quran-assets.mjs <raw-dir> <assets-dir>
//   raw-dir يحتوي على: quran-uthmani.json (alquran.cloud)، quran-com-uthmani.json (api.quran.com)،
//   quran-simple.json (alquran.cloud quran-simple-clean)، muyassar.json (alquran.cloud ar.muyassar)
// لا يُعدَّل أي حرف من النص القرآني: الإزالة الوحيدة هي البسملة المكررة في أول آية من كل سورة (عدا الفاتحة والتوبة)
// وعلامة BOM غير المرئية. وأي فشل في التحقق يوقف البناء.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const [rawDir, assetsDir] = process.argv.slice(2);
if (!rawDir || !assetsDir) { console.error('usage: build-quran-assets.mjs <raw> <assets>'); process.exit(2); }

const readJson = (f) => JSON.parse(fs.readFileSync(path.join(rawDir, f), 'utf8'));
const sha256 = (f) => crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
const fail = (m) => { console.error('FAIL: ' + m); process.exit(1); };

// عدد آيات كل سورة وفق رواية حفص عن عاصم (المرجع المستقل لهذا الفحص)
const EXPECTED = [7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6];
if (EXPECTED.length !== 114) fail('expected table length');
if (EXPECTED.reduce((a, b) => a + b, 0) !== 6236) fail('expected table sum != 6236');

const skeleton = (t) => t
  .replace(/ـ/g, "")               // تطويل
  .replace(/ٰ/g, "ا")               // ألف خنجرية تُعامل كألف إملائي عند المقارنة فقط
  .replace(/[ٱأإآ]/g, "ا") // ألف وصل وأنواع الهمزة
  .replace(/ى/g, "ي").replace(/ة/g, "ه")
  .replace(/ؤ/g, "و").replace(/ئ/g, "ي").replace(/ء/g, "")
  .replace(/[^ء-ي]/g, "")      // حذف الحركات وعلامات الوقف
  .replace(/ا+/g, "ا");             // اختلاف كتابة المد بين الرسمين
const uth = readJson('quran-uthmani.json').data;
const qcom = readJson('quran-com-uthmani.json').verses;
const muy = readJson('muyassar.json').data;

// ---------- 1) البنية ----------
if (uth.surahs.length !== 114) fail('surah count');
const BOM = /^﻿/;
const bismillah = uth.surahs[0].ayahs[0].text.replace(BOM, '').trim();
const flat = [];
let gid = 0;
uth.surahs.forEach((s, si) => {
  if (s.number !== si + 1) fail('surah order ' + s.number);
  if (s.ayahs.length !== EXPECTED[si]) fail(`ayah count surah ${s.number}: ${s.ayahs.length} != ${EXPECTED[si]}`);
  s.ayahs.forEach((a, ai) => {
    gid++;
    if (a.number !== gid) fail(`global ayah number ${s.number}:${a.numberInSurah} -> ${a.number} != ${gid}`);
    if (a.numberInSurah !== ai + 1) fail(`ayah order ${s.number}:${a.numberInSurah}`);
    let text = a.text.replace(BOM, '').trim();
    if (ai === 0 && s.number !== 1 && s.number !== 9) {
      // في مصدرين نصّ البسملة فيه شدّة زائدة (95 و97)، لذلك نطابق بهيكل الحروف ثم نحذف أول 4 كلمات
      const tokens = text.split(" ");
      if (skeleton(tokens.slice(0, 4).join(" ")) !== skeleton(bismillah)) fail(`bismillah prefix missing ${s.number}:1`);
      text = tokens.slice(4).join(" ").trim();
    } else if (ai === 0 && s.number === 9 && text.startsWith(bismillah)) {
      fail('bismillah present in At-Tawbah');
    }
    if (!text) fail(`empty text ${s.number}:${a.numberInSurah}`);
    flat.push({ s: s.number, a: a.numberInSurah, text, page: a.page, juz: a.juz, hq: a.hizbQuarter, sajda: a.sajda ? 1 : 0, ruku: a.ruku });
  });
});
if (flat.length !== 6236) fail('total ayahs ' + flat.length);

// الصفحات 1..604 متصلة غير متناقصة، والأجزاء 1..30، وأرباع الأحزاب 1..240
let prevPage = 1, prevJuz = 1, prevHq = 1;
for (const a of flat) {
  if (a.page < prevPage || a.page - prevPage > 1) fail(`page jump at ${a.s}:${a.a}`);
  if (a.juz < prevJuz || a.juz - prevJuz > 1) fail(`juz jump at ${a.s}:${a.a}`);
  if (a.hq < prevHq || a.hq - prevHq > 1) fail(`hizbQuarter jump at ${a.s}:${a.a}`);
  prevPage = a.page; prevJuz = a.juz; prevHq = a.hq;
}
if (flat[0].page !== 1 || prevPage !== 604) fail('page range ' + prevPage);
if (flat[0].juz !== 1 || prevJuz !== 30) fail('juz range ' + prevJuz);
if (flat[0].hq !== 1 || prevHq !== 240) fail('hizbQuarter range ' + prevHq);
const sajdaCount = flat.filter(a => a.sajda).length;
if (sajdaCount !== 15) fail('sajda count ' + sajdaCount);

// ---------- 2) المطابقة الحرفية مع مصدر مستقل (api.quran.com) بهيكل الحروف ----------
// ترميز Tanzil يكتب «صَىٰحِبَىِ» في 12:39 و12:41 بينما يكتبها المصدر الثاني «صَـٰحِبَىِ»: اختلاف ترميز معروف لا اختلاف نص
const KNOWN_ENCODING_DIFFS = new Set(['12:39', '12:41']);
let mismatchQcom = 0;
const qmap = new Map(qcom.map(v => [v.verse_key, v.text_uthmani]));
if (qmap.size !== 6236) fail('quran.com count ' + qmap.size);
flat.forEach((a) => {
  const key = a.s + ':' + a.a;
  const other = qmap.get(key);
  if (skeleton(other) !== skeleton(a.text) && !KNOWN_ENCODING_DIFFS.has(key)) {
    mismatchQcom++; if (mismatchQcom <= 5) console.warn('quran.com mismatch', key);
  }
});
console.log('skeleton mismatches vs quran.com: ' + mismatchQcom);
if (mismatchQcom > 0) fail('text mismatch against independent source');

// ---------- 3) التفسير الميسر ----------
const muyFlat = muy.surahs.flatMap(s => s.ayahs);
if (muyFlat.length !== 6236) fail('muyassar count');
muyFlat.forEach((a, i) => { if (a.number !== i + 1) fail('muyassar order ' + i); });
const muyTexts = muyFlat.map(a => a.text.replace(BOM, '').trim());
if (muyTexts.some(t => !t)) fail('empty tafsir entry');

// ---------- 4) كتابة الملفات ----------
const surahs = uth.surahs.map((s, si) => {
  const first = flat.findIndex(a => a.s === s.number) + 1;
  return {
    n: s.number,
    ar: s.name.replace(/^سُورَةُ\s+/, '').replace(/^سورة\s+/, ''),
    en: s.englishName,
    meccan: s.revelationType === 'Meccan',
    count: EXPECTED[si],
    first,
  };
});
const out = {
  format: 1,
  source: 'alquran.cloud edition quran-uthmani (Tanzil Uthmani text), cross-checked letter-by-letter with api.quran.com',
  bismillah,
  surahs,
  // [surah, ayahInSurah, text, page, juz, hizbQuarter, sajda]
  ayahs: flat.map(a => [a.s, a.a, a.text, a.page, a.juz, a.hq, a.sajda]),
};
fs.mkdirSync(path.join(assetsDir, 'quran'), { recursive: true });
fs.mkdirSync(path.join(assetsDir, 'tafsir'), { recursive: true });
const quranFile = path.join(assetsDir, 'quran', 'quran.json');
fs.writeFileSync(quranFile, JSON.stringify(out));
const tafsirFile = path.join(assetsDir, 'tafsir', 'ar.muyassar.json');
fs.writeFileSync(tafsirFile, JSON.stringify({
  edition: 'ar.muyassar', name: 'التفسير الميسر',
  author: 'نخبة من العلماء بإشراف مجمع الملك فهد لطباعة المصحف الشريف',
  texts: muyTexts,
}));

const report = {
  generatedFrom: Object.fromEntries(['quran-uthmani.json', 'quran-com-uthmani.json', 'muyassar.json'].map(f => [f, sha256(path.join(rawDir, f))])),
  outputs: { 'quran/quran.json': sha256(quranFile), 'tafsir/ar.muyassar.json': sha256(tafsirFile) },
  surahs: 114, ayahs: 6236, pages: 604, juz: 30, hizbQuarters: 240, sajdas: sajdaCount,
};
fs.writeFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'data', 'quran-verification.json'), JSON.stringify(report, null, 2));
console.log('OK', JSON.stringify(report.outputs));
