# يقين — YAQEEN

تطبيق إسلامي متكامل لنظام Android يجمع **القرآن الكريم والأذكار والصلوات والسنن والمهام والتقويم والإحصاءات الشخصية** في مكان واحد. يعمل **دون إنترنت**، ويتزامن بين أجهزة المستخدم بحساب Google، بتصميم هادئ وراقٍ (RTL).

> الجودة والدقة والموثوقية قبل كثرة المميزات.

## المميزات
- **القرآن:** مصحف كامل بالرسم العثماني محفوظ داخل التطبيق ومتحقق منه (6236 آية)، فهارس السور/الأجزاء/الأحزاب/الأرباع، بحث، تمرير تلقائي، علامات مرجعية بألوان وتصنيفات، أوراد قرآنية متعددة بتذكير ومتابعة، تلاوة (مع التنزيل للعمل دون إنترنت)، تفسير (الميسر مضمّن + تنزيل غيره)، مشاركة الآيات نصًا وصورة، واستئناف آخر موضع قراءة على أي جهاز.
- **الأذكار:** صباح، مساء، نوم، استيقاظ، بعد الصلاة، متنوعة — بطاقات بعدّاد تفاعلي وانتقال تلقائي ومصدر وفضل معتمد وتذكيرات.
- **الصلاة:** تسجيل الصلوات الخمس (جماعة/بيت/لم أصلِّ بعد) والسنن الرواتب، مواقيت وقبلة وأذان بأصوات قابلة للتخصيص.
- **المهام اليومية** المرتبطة بسجلات العبادة الفعلية، **التقويم** الهجري والميلادي والمناسبات الإسلامية.
- **رحلتي مع يقين:** إحصاءات شخصية هادئة بلا ترتيب ولا منافسة ولا نقاط، وغير المسجَّل لا يُحسب تقصيرًا.
- **مزامنة Local-First** مع Firestore، وإشعارات موحدة بتحكم مستقل، وتحديث عبر GitHub Releases.

## التقنيات
Kotlin · Jetpack Compose (Material 3) · MVVM / Clean Architecture · Room · Firebase (Auth, Firestore, FCM) · WorkManager · Media3 · adhan. التفاصيل: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## البناء السريع
> **مهم:** مسار المشروع يجب أن يكون **ASCII** (بدون حروف عربية) لأن مترجم Kotlin/KSP يفسد المسارات العربية.

المتطلبات: JDK 17 · Android SDK 35 · (Android Studio اختياري).
```powershell
# على جهاز التطوير الحالي (الأدوات في C:\Users\hp\yaqeen-toolchain)
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 :app:assembleDebug
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 :app:testDebugUnitTest
```
أو افتح المجلد في Android Studio (وأنشئ `local.properties` بـ `sdk.dir=...`).

يعمل التطبيق بدون Firebase (وضع محلي). لتفعيل الحساب والمزامنة اتبع [`firebase/README.md`](firebase/README.md).

## الوثائق
[خطة المشروع](docs/PROJECT_PLAN.md) · [المعمارية](docs/ARCHITECTURE.md) · [مخطط البيانات](docs/DATABASE_SCHEMA.md) · [العمل دون إنترنت والمزامنة](docs/OFFLINE_SYNC.md) · [الأمان](docs/SECURITY.md) · [عملية الإصدار](docs/RELEASE_PROCESS.md) · [سجل التغييرات](CHANGELOG.md)

## المصادر والتراخيص
نص القرآن: Tanzil (رسم عثماني) — يُنسب ولا يُعدَّل. التفسير الميسر: مجمع الملك فهد لطباعة المصحف الشريف. الأذكار: قاعدة Morning-And-Evening-Adhkar-DB (MIT) وحصن المسلم. الخطوط: Amiri وAmiri Quran وIBM Plex Sans Arabic (OFL، انظر `docs/licenses/`). حساب المواقيت: مكتبة adhan من batoulapps (MIT).
