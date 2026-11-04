# البنية المعمارية — يقين (YAQEEN)

## 1. نظرة عامة
تطبيق Android أصلي (Kotlin) بواجهة Jetpack Compose (Material 3)، عربي RTL بالكامل، يعمل **دون إنترنت** ويُزامن بين أجهزة المستخدم عند الاتصال بحساب Google.

```
┌────────────────────────── واجهة المستخدم (Compose) ──────────────────────────┐
│ feature/home  quran  adhkar  prayer  tasks  calendar  journey  settings        │
│        ViewModel (StateFlow)  ←  Flow من Room                                  │
└───────────────┬───────────────────────────────────────────────────────────────┘
                │ يقرأ Flow · يكتب عبر SyncWriter فقط
┌───────────────▼────────── البيانات (data/) ───────────────────────────────────┐
│ local/  Room: كيانات + DAOs (مصدر الحقيقة لبيانات المستخدم)                    │
│ quran/ adhkar/  بيانات ثابتة من assets (للقراءة فقط، نص موثّق وبصمة SHA-256)   │
│ sync/   SyncWriter · SyncManager · RoomSyncTable · RemoteStore(Firestore)      │
│ settings/ prefs/ auth/                                                         │
└───────────────┬───────────────────────────────────────────────────────────────┘
                │
┌───────────────▼─────────── الخلفية (بدون خدمات دائمة) ────────────────────────┐
│ AlarmManager (تذكيرات دقيقة) · WorkManager (مزامنة دورية، إعادة جدولة)        │
│ FCM (إشعار تحديث التطبيق فقط) · Media3 (تشغيل التلاوة وقت الاستماع فقط)       │
└───────────────────────────────────────────────────────────────────────────────┘
```

## 2. لماذا هذه القرارات؟
| القرار | السبب |
|---|---|
| وحدة (module) واحدة بحزم طبقية بدل وحدات متعددة | بناء أسرع على أجهزة محدودة الذاكرة؛ الفصل المنطقي محفوظ بالحزم (`domain`, `data`, `feature`, `ui`)، ويمكن فصلها لاحقًا |
| حقن يدوي (`AppContainer`) بدل Hilt | تقليل تعقيد/أخطاء معالجات الشيفرة (KSP+Hilt) والإقلاع أسرع؛ الواجهة نفسها تبقى قابلة للاختبار |
| القرآن في assets وليس Room | نص ثابت لا يتغير: لا حاجة لقاعدة بيانات، وحمله مرة في الذاكرة أسرع وأبسط، ويسهل فحص بصمته |
| Room + Firestore بدل Firestore وحده | العمل دون إنترنت وسرعة الاستجابة، والمزامنة تتم في الخلفية |
| مزامنة قائمة على الحالة (state-based) مع معرّفات حتمية | تمنع التكرار وتُبسّط حل التعارض (انظر OFFLINE_SYNC.md) |
| Kotlin compiler داخل Gradle daemon | الـ Kotlin daemon المستقل لا يتصل على بعض الأجهزة |

## 3. هيكل المجلدات
```
YAQEEN/
├─ app/src/main/
│  ├─ AndroidManifest.xml
│  ├─ assets/ quran/quran.json · tafsir/ar.muyassar.json · adhkar/adhkar.json · cities/
│  ├─ res/ font/ (Amiri Quran, Amiri, IBM Plex Sans Arabic) drawable/ values/ xml/
│  └─ java/app/yaqeen/
│     ├─ YaqeenApp.kt · MainActivity.kt · AppContainer.kt
│     ├─ core/        Clock/HybridClock · ArabicText · HijriCalendar
│     ├─ domain/model Prayer · PrayerStatus · Sunnah · TaskLink ...
│     ├─ data/
│     │   ├─ local/   Entities · Daos · YaqeenDatabase
│     │   ├─ sync/    SyncCore · SyncManager · FirestoreRemoteStore · SyncSupport
│     │   ├─ quran/ adhkar/ settings/ prefs/ auth/
│     ├─ feature/ home quran adhkar prayer tasks calendar journey settings
│     ├─ notifications/  القنوات · ReminderScheduler · الاستقبال · إعادة الجدولة · FCM
│     ├─ work/        SyncWorker · RescheduleWorker
│     └─ ui/          theme · components · navigation · AppRoot
├─ app/src/test/      اختبارات JVM (بيانات، مزامنة، منطق كل ميزة)
├─ firebase/          firestore.rules · indexes · أدلة الإعداد
├─ tools/             بناء بيانات القرآن/الأذكار (مع التحقق) · gradle.ps1 · أدوات الإصدار
├─ docs/              هذه الوثائق + تراخيص الخطوط والمصادر
└─ .github/workflows/ CI والإصدار
```

## 4. المكتبات (الإصدارات مثبّتة ومتوافقة)
Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.9 · Compose BOM 2024.12.01 (Material3 1.3) · Navigation Compose 2.8.5 · Room 2.6.1 (+KSP 2.0.21-1.0.28) · WorkManager 2.9.1 · DataStore 1.1.1 · Media3 1.5.0 · kotlinx-serialization 1.7.3 · Coroutines 1.9.0 · Firebase BOM 33.7.0 (Auth, Firestore, Messaging) · Credential Manager 1.3.0 + googleid 1.1.1 · adhan (batoulapps) 1.2.1. minSdk 26 · targetSdk 35.

## 5. خدمات Firebase المطلوبة
Authentication (Google) · Cloud Firestore (+ القواعد) · Cloud Messaging (موضوع `app_updates`). لا Analytics ولا Crashlytics (خصوصية). التفاصيل في `firebase/README.md`.

## 6. مصادر المحتوى
| المحتوى | المصدر | التحقق |
|---|---|---|
| نص القرآن (رسم عثماني) | Tanzil (عبر alquran.cloud `quran-uthmani`) | 114 سورة / 6236 آية / ترتيب وعدد كل سورة / 604 صفحة / 30 جزءًا / 240 ربع حزب / 15 سجدة؛ مطابقة حرفية لمصدر مستقل (api.quran.com) بلا اختلافات؛ بصمة SHA-256 في اختبار؛ لا توليد بالذكاء الاصطناعي ولا تعديل |
| الخط | Amiri Quran (OFL) | — |
| التفسير | التفسير الميسر (مجمع الملك فهد) مضمّن، وتفاسير أخرى بالتنزيل من alquran.cloud | عدد 6236 مدخلًا مطابق |
| الأذكار | صباح/مساء: Morning-And-Evening-Adhkar-DB (MIT) مع المصدر والفضل؛ بقية الأقسام: حصن المسلم؛ نصوص الآيات داخل الأذكار تؤخذ من المصحف المتحقق منه | اختبارات: لا تكرار، لكل ذكر مصدر، التطابق مع المصحف |

> **تنبيه صدق:** الأذكار تحتاج مراجعة علمية بشرية قبل النشر العام؛ العزو في الأقسام غير الصباح/المساء على مستوى كتاب الحديث (رواه البخاري...) دون أرقام أحاديث.

## 7. الاختبارات
- JVM: سلامة المصحف والأذكار، محرك المزامنة (جهازان وهميان، انقطاع، تعارض، رفض الخادم للأقدم، حذف، تكرار)، منطق كل ميزة.
- التكامل: بناء Debug + Release موقّع (R8)، فحص الـ APK، تشغيل على جهاز/محاكي (غير متاح في بيئة التطوير الآلي — انظر PROJECT_PLAN.md للمنجز والمؤجل).
