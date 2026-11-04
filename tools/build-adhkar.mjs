// بناء ملف الأذكار المحلي.
// الاستخدام: node tools/build-adhkar.mjs <raw-dir> <assets-dir>
// المصادر:
//  1) Seen-Arabic/Morning-And-Evening-Adhkar-DB (MIT): أذكار الصباح والمساء مع المصدر والفضل (seen-ar.json)
//  2) rn0x/Adhkar-json (حصن المسلم): النصوص لبقية الأقسام (adhkar-rn0x.json)
//  3) quran.json المبني والمتحقق منه: نصوص الآيات (آية الكرسي، خواتيم البقرة، المعوذات، خواتيم آل عمران)
// القواعد: لا فضل يُضاف إلا إذا كان نص حديث ثابت في الباب، والعزو على مستوى الكتاب (رواه البخاري...) دون أرقام أحاديث من الذاكرة.
import fs from 'node:fs';
import path from 'node:path';

const [rawDir, assetsDir] = process.argv.slice(2);
if (!rawDir || !assetsDir) { console.error('usage: build-adhkar.mjs <raw> <assets>'); process.exit(2); }
const fail = (m) => { console.error('FAIL: ' + m); process.exit(1); };
const rd = (f) => JSON.parse(fs.readFileSync(path.join(rawDir, f), 'utf8'));

const seen = rd('seen-ar.json');
const hisn = rd('adhkar-rn0x.json');
const quran = JSON.parse(fs.readFileSync(path.join(assetsDir, 'quran', 'quran.json'), 'utf8'));
const hisnItem = (cat, id) => {
  const c = hisn.find(x => x.id === cat); if (!c) fail('hisn cat ' + cat);
  const it = c.array.find(x => x.id === id); if (!it) fail(`hisn item ${cat}/${id}`);
  return it.text;
};

const ws = (t) => t.replace(/\s+/g, ' ').trim();
const balanced = (x) => { let d = 0; for (const c of x) { if (c === '(') d++; else if (c === ')') { d--; if (d < 0) return false; } } return d === 0; };
const TRAILING_DOTS = /\.+$/;
const unwrap = (t) => {
  let s = ws(t).replace(TRAILING_DOTS, '').trim();
  for (;;) {
    const before = s;
    if (s.startsWith('((') && s.endsWith('))') && balanced(s.slice(2, -2))) s = s.slice(2, -2).trim();
    else if (s.startsWith('(') && s.endsWith(')') && balanced(s.slice(1, -1))) s = s.slice(1, -1).trim();
    s = s.replace(TRAILING_DOTS, '').trim();
    if (s === before) break;
  }
  return s;
};
// يحذف أقواس الحصن الداخلية ((..)) و[..] التي تفصل بين أجزاء الذكر
const stripBrackets = (t) => ws(t.replace(/\(\(|\)\)/g, ' ').replace(/[\[\]]/g, ' '));
// يفصل التعليمات التي تسبق «ويقول:» عن نص الذكر
const afterSays = (t) => { const i = t.indexOf('وَيَقُولُ:'); return i < 0 ? stripBrackets(unwrap(t)) : stripBrackets(t.slice(i + 'وَيَقُولُ:'.length)).replace(TRAILING_DOTS, '').trim(); };

// نص آيات من ملف القرآن المتحقق منه: refs = [[sura, from, to], ...]
const END_MARK = '۝'; // ۝ علامة نهاية الآية (للعرض فقط، بدون رقم)
const quranText = (refs) => refs.map(([s, a, b]) => {
  const out = [];
  for (let n = a; n <= b; n++) {
    const row = quran.ayahs.find(r => r[0] === s && r[1] === n); if (!row) fail(`quran ref ${s}:${n}`);
    out.push(row[2]);
  }
  return out.join(' ');
}).join(' ');
const bismillahThen = (s) => quran.bismillah + ' ' + quranText([[s, 1, quran.surahs[s - 1].count]]);
const QULS = [112, 113, 114].map(bismillahThen).join(' ');

const mk = (cat, n, o) => ({ id: `${cat}_${String(n).padStart(2, '0')}`, text: o.text, count: o.count ?? 1, source: o.source ?? '', fadl: o.fadl ?? '', note: o.note ?? '', group: o.group ?? '', quranRef: o.quranRef ?? '' });
const numberAll = (cat, list) => list.map((o, i) => mk(cat, i + 1, o));

// ---------- الصباح والمساء ----------
const QURAN_OVERRIDE = {
  2: { text: quranText([[2, 255, 255]]), quranRef: '2:255' },
  3: { text: quranText([[2, 285, 286]]), quranRef: '2:285-286' },
  4: { text: bismillahThen(112), quranRef: '112' },
  5: { text: bismillahThen(113), quranRef: '113' },
  6: { text: bismillahThen(114), quranRef: '114' },
};
const fromSeen = (type) => seen
  .filter(x => !x.type || x.type === type || x.type === 0)
  .sort((a, b) => a.order - b.order)
  .map(x => ({
    text: QURAN_OVERRIDE[x.order]?.text ?? ws(x.content),
    quranRef: QURAN_OVERRIDE[x.order]?.quranRef ?? '',
    count: x.count,
    source: ws(x.source || '').replace(/^[،,\s]+/, ''),
    fadl: ws(x.fadl || ''),
  }));
const morning = numberAll('morning', fromSeen(1));
const evening = numberAll('evening', fromSeen(2));

// ---------- الاستيقاظ ----------
const waking = numberAll('waking', [
  { text: unwrap(hisnItem(3, 1)), source: 'رواه البخاري' },
  { text: unwrap(hisnItem(3, 2)), source: 'رواه البخاري' },
  { text: unwrap(hisnItem(3, 3)), source: 'رواه الترمذي' },
  { text: quranText([[3, 190, 200]]), quranRef: '3:190-200', source: 'رواه البخاري ومسلم', note: 'خواتيم سورة آل عمران عند القيام من الليل' },
]);

// ---------- النوم ----------
const sleep = numberAll('sleep', [
  { text: quranText([[2, 255, 255]]), quranRef: '2:255', source: 'رواه البخاري', fadl: 'لا يزال عليك من الله حافظ، ولا يقربنك شيطان حتى تصبح' },
  { text: quranText([[2, 285, 286]]), quranRef: '2:285-286', source: 'رواه البخاري ومسلم', fadl: 'من قرأ بالآيتين من آخر سورة البقرة في ليلة كفتاه' },
  { text: QULS, count: 3, quranRef: '112-114', source: 'رواه البخاري', note: 'يجمع كفّيه ثم ينفث فيهما ويقرأ المعوذات، ثم يمسح بهما ما استطاع من جسده، يبدأ برأسه ووجهه، يفعل ذلك ثلاث مرات' },
  { text: unwrap(hisnItem(2, 4)), source: 'رواه البخاري ومسلم' },
  { text: unwrap(hisnItem(2, 5)), source: 'رواه مسلم' },
  { text: unwrap(hisnItem(2, 6)), source: 'رواه أبو داود والترمذي', note: 'يضع يده اليمنى تحت خدّه' },
  { text: unwrap(hisnItem(2, 7)), source: 'رواه البخاري' },
  { text: 'سُبْحَانَ اللَّهِ', count: 33, source: 'رواه البخاري ومسلم' },
  { text: 'الْحَمْدُ لِلَّهِ', count: 33, source: 'رواه البخاري ومسلم' },
  { text: 'اللَّهُ أَكْبَرُ', count: 34, source: 'رواه البخاري ومسلم' },
  { text: unwrap(hisnItem(2, 9)), source: 'رواه مسلم' },
  { text: unwrap(hisnItem(2, 10)), source: 'رواه مسلم' },
  { text: unwrap(hisnItem(2, 11)), source: 'رواه الترمذي وأبو داود' },
  { text: unwrap(hisnItem(2, 12)), source: 'رواه الترمذي والنسائي', note: 'قراءة سورة السجدة وسورة الملك' },
  { text: unwrap(hisnItem(2, 13)), source: 'رواه البخاري ومسلم' },
]);

// ---------- بعد الصلاة ----------
const apRaw1 = unwrap(hisnItem(27, 1));
const apM = apRaw1.match(/\([^)]*\)/);
if (!apM) fail('after-prayer item1 split marker');
const apIdx = apM.index, apLen = apM[0].length;
const apTahlilRaw = unwrap(hisnItem(27, 4));
const apT = apTahlilRaw.match(/\([^)]*\)/);
if (!apT) fail('after-prayer tasbih marker');
const apTahlil = ws(apTahlilRaw.slice(apT.index + apT[0].length));
const tenRaw = unwrap(hisnItem(27, 7));
const tenCut = tenRaw.indexOf('))');
if (tenCut < 0) fail('after-prayer item7 split');
const afterPrayer = numberAll('after_prayer', [
  { text: apRaw1.slice(0, apIdx).trim(), count: 3, source: 'رواه مسلم' },
  { text: ws(apRaw1.slice(apIdx + apLen)), source: 'رواه مسلم' },
  { text: unwrap(hisnItem(27, 2)), source: 'رواه البخاري ومسلم' },
  { text: unwrap(hisnItem(27, 3)), source: 'رواه مسلم' },
  { text: 'سُبْحَانَ اللَّهِ', count: 33, source: 'رواه مسلم' },
  { text: 'الْحَمْدُ لِلَّهِ', count: 33, source: 'رواه مسلم' },
  { text: 'اللَّهُ أَكْبَرُ', count: 33, source: 'رواه مسلم' },
  { text: apTahlil, source: 'رواه مسلم', fadl: 'غُفرت خطاياه وإن كانت مثل زبد البحر (لمن قال ما سبق وختم بهذا الذكر)' },
  { text: quranText([[2, 255, 255]]), quranRef: '2:255', source: 'رواه النسائي في السنن الكبرى', fadl: 'من قرأ آية الكرسي دبر كل صلاة مكتوبة لم يمنعه من دخول الجنة إلا أن يموت' },
  { text: QULS, quranRef: '112-114', source: 'رواه أبو داود والترمذي والنسائي', note: 'تُقرأ مرة بعد كل صلاة، وثلاث مرات بعد الفجر والمغرب' },
  { text: unwrap(tenRaw.slice(0, tenCut + 2)), count: 10, source: 'رواه الترمذي وأحمد', note: 'بعد صلاتي المغرب والصبح' },
  { text: unwrap(hisnItem(27, 8)).replace(/\)\)\s*بَعْدَ.*$/, '').replace(/^\(\(/, '').trim(), source: 'رواه ابن ماجه', note: 'بعد السلام من صلاة الفجر' },
]);

// ---------- متنوعة ----------
const G = {
  toilet: 'دخول الخلاء والخروج منه', wudu: 'بعد الوضوء', home: 'المنزل', mosque: 'المسجد', worry: 'الهم والكرب',
  food: 'الطعام', dress: 'اللباس', travel: 'السفر', anger: 'الغضب', tasbih: 'التسبيح والاستغفار',
};
const misc = numberAll('misc', [
  { group: G.toilet, text: unwrap(hisnItem(4, 1)), source: 'رواه البخاري ومسلم' },
  { group: G.toilet, text: unwrap(hisnItem(5, 1)), source: 'رواه أبو داود والترمذي وابن ماجه' },
  { group: G.wudu, text: unwrap(hisnItem(7, 1)), source: 'رواه مسلم' },
  { group: G.wudu, text: unwrap(hisnItem(7, 2)), source: 'رواه الترمذي' },
  { group: G.home, text: unwrap(hisnItem(8, 1)), source: 'رواه أبو داود والترمذي', note: 'عند الخروج من المنزل' },
  { group: G.home, text: unwrap(hisnItem(8, 2)), source: 'رواه أبو داود والترمذي والنسائي', note: 'عند الخروج من المنزل' },
  { group: G.home, text: unwrap(hisnItem(9, 1)), source: 'رواه أبو داود', note: 'عند دخول المنزل' },
  { group: G.mosque, text: unwrap(hisnItem(10, 1).slice(0, hisnItem(10, 1).indexOf(')([') + 1)), source: 'رواه البخاري ومسلم', note: 'عند الذهاب إلى المسجد' },
  { group: G.mosque, text: afterSays(hisnItem(11, 1)), source: 'رواه أبو داود ومسلم', note: 'عند دخول المسجد، يبدأ برجله اليمنى' },
  { group: G.mosque, text: afterSays(hisnItem(12, 1)), source: 'رواه مسلم وابن ماجه', note: 'عند الخروج من المسجد، يبدأ برجله اليسرى' },
  { group: G.worry, text: unwrap(hisnItem(34, 1)), source: 'رواه أحمد' },
  { group: G.worry, text: unwrap(hisnItem(34, 2)), source: 'رواه البخاري' },
  { group: G.worry, text: unwrap(hisnItem(35, 1)), source: 'رواه البخاري ومسلم' },
  { group: G.worry, text: unwrap(hisnItem(35, 2)), source: 'رواه أبو داود' },
  { group: G.worry, text: unwrap(hisnItem(35, 3)), source: 'رواه الترمذي' },
  { group: G.food, text: unwrap(hisnItem(70, 1)), source: 'رواه أبو داود والترمذي', note: 'عند الفراغ من الطعام' },
  { group: G.food, text: unwrap(hisnItem(70, 2)), source: 'رواه البخاري', note: 'عند الفراغ من الطعام' },
  { group: G.dress, text: unwrap(hisnItem(14, 1)).replace(/\.\.\.$/, '').trim(), source: 'رواه أبو داود والترمذي' },
  { group: G.travel, text: stripBrackets(hisnItem(96, 1)), source: 'رواه مسلم' },
  { group: G.anger, text: unwrap(hisnItem(82, 1)), source: 'رواه البخاري ومسلم' },
  { group: G.tasbih, text: 'سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ', source: 'رواه البخاري ومسلم', fadl: 'كلمتان خفيفتان على اللسان، ثقيلتان في الميزان، حبيبتان إلى الرحمن' },
  { group: G.tasbih, text: 'لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ', source: 'رواه البخاري ومسلم', fadl: 'كنز من كنوز الجنة' },
  { group: G.tasbih, text: 'أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ الَّذِي لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ وَأَتُوبُ إِلَيْهِ', source: 'رواه أبو داود والترمذي' },
]);

const categories = [
  { id: 'morning', title: 'أذكار الصباح', items: morning },
  { id: 'evening', title: 'أذكار المساء', items: evening },
  { id: 'sleep', title: 'أذكار النوم', items: sleep },
  { id: 'waking', title: 'أذكار الاستيقاظ', items: waking },
  { id: 'after_prayer', title: 'أذكار بعد الصلاة', items: afterPrayer },
  { id: 'misc', title: 'أذكار متنوعة', items: misc },
];

// ---------- التحقق ----------
const ids = new Set();
for (const c of categories) {
  if (!c.items.length) fail('empty category ' + c.id);
  for (const it of c.items) {
    if (ids.has(it.id)) fail('dup id ' + it.id); ids.add(it.id);
    if (!it.text || it.text.length < 3) fail('empty text ' + it.id);
    if (!Number.isInteger(it.count) || it.count < 1) fail('bad count ' + it.id);
    if (/\(\(|\)\)/.test(it.text)) fail('leftover wrapper ' + it.id + ' :: ' + it.text.slice(0, 60));
    if (!it.source) fail('missing source ' + it.id);
  }
}
const out = {
  format: 1,
  notice: 'نصوص الصباح والمساء من قاعدة Morning-And-Evening-Adhkar-DB (MIT)، وبقية الأقسام من حصن المسلم؛ نصوص الآيات من المصحف المحلي المتحقق منه. يُراجَع المحتوى مراجعة علمية قبل النشر العام.',
  categories,
};
fs.mkdirSync(path.join(assetsDir, 'adhkar'), { recursive: true });
fs.writeFileSync(path.join(assetsDir, 'adhkar', 'adhkar.json'), JSON.stringify(out));
console.log('OK', categories.map(c => `${c.id}:${c.items.length}`).join(' '));
