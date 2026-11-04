# دليل التنفيذ المشترك لميزات يقين (مرجع داخلي للمطورين)

هذا الملف هو **العقد** بين الأساس المشترك (core) والميزات. اقرأه كاملًا قبل كتابة أي كود.

## 1. القواعد العامة
- اللغة: Kotlin + Jetpack Compose + Material3. الواجهات **RTL عربية بالكامل**، كل النصوص عربية، الأرقام تُعرض بالهندية عند الحاجة عبر `ArabicText.indic()`.
- **لا بيانات وهمية**: كل شاشة تقرأ من Room/الأصول الحقيقية. لا أزرار أو صفحات شكلية بلا وظيفة.
- **لا تعدّل النص القرآني أبدًا** ولا تولّد نصوصًا دينية. القرآن من `QuranData` والأذكار من `AdhkarCatalog` فقط.
- المستخدم قد يعمل **بلا إنترنت**: لا تتطلب أي شاشة أساسية الاتصال. الشبكة فقط للتنزيل/الصوت/التفسير الإضافي/التحديث.
- لا تُضمّن أي مفتاح أو سر. لا تُنشئ خدمات خلفية دائمة (Foreground Service) إلا لتشغيل الصوت أثناء التشغيل.
- حافظ على الهوية: لا ألوان ثابتة خارج `MaterialTheme.colorScheme` و`YaqeenTheme.extra`، استخدم `YaqeenCard`, `YaqeenTopBar`, `SectionTitle`... من `ui/components`.
- **لا تعدّل ملفات خارج مجلد ميزتك** إلا ما يُذكر هنا. إن احتجت تغييرًا في ملف مشترك، اكتبه في تقريرك النهائي (قسم "طلبات تكامل") وسيُدمَج.
  - يُسمح لك بإضافة استعلامات في نهاية أي DAO في `data/local/Daos.kt` (بدون تغيير الأعمدة/الجداول).
  - يُسمح بإضافة مكوّنات manifest (service/receiver/activity) **قبل** السطر `<!-- AGENT_COMPONENTS_END -->` فقط.
  - الموارد: ضع نصوصك في `res/values/strings_<feature>.xml` ومواردك في ملفات باسم ميزتك لتفادي التعارض.
- الأسماء: الحزمة `app.yaqeen.feature.<feature>`؛ كل شاشة لها ViewModel يُنشأ بـ `containerViewModel { MyVm(it) }` (من `ui/AppRoot.kt`).

## 2. الأساس المشترك (اقرأ الملفات نفسها)
| الملف | الغرض |
|---|---|
| `AppContainer.kt` | حاوية الاعتماديات: `db`, `writer`, `settings`, `localPrefs`, `auth`, `sync`, `reminders`, `clock`, `quran()`, `adhkar()`, `requestReschedule()` |
| `data/local/Entities.kt` | كل الكيانات. لكل كيان حقول المزامنة (`updatedAt, deleted, dirty, opId, deviceId`) |
| `data/local/Daos.kt` | كل الـDAOs (Flow للقراءة، `range(from,to)` للقراءة المباشرة) |
| `data/sync/SyncSupport.kt` | `SyncWriter` — **نقطة الكتابة الوحيدة** |
| `data/settings/SettingsRepository.kt` | الإعدادات المنمّطة `Settings.*` + `settings.observe/get/set` |
| `data/prefs/LocalPrefs.kt` | تفضيلات الجهاز غير المتزامنة (الموقع، onboarding...) |
| `data/quran/QuranData.kt` | النص القرآني المحلي والفهرسة والبحث |
| `data/adhkar/AdhkarCatalog.kt` | نصوص الأذكار |
| `core/HijriCalendar.kt` | التاريخ الهجري (أم القرى) + تعديل يدوي |
| `domain/model/Domain.kt` | `Prayer`, `PrayerStatus`, `Sunnah`, `TaskLink`, ... |
| `notifications/Notifications.kt` | `Reminder`, `ReminderScheduler`, القنوات |
| `ui/navigation/Routes.kt` | المسارات ودوال البناء المساعدة |
| `ui/theme`, `ui/components` | الثيم والمكونات الموحدة |

### الكتابة (مهم جدًا)
```kotlin
// دائمًا عبر writer، لا dao.upsert مباشرة:
c.writer.save(c.db.prayerLogs(), PrayerLogEntity(id = "${date}_${prayer.name}", date = date, prayer = prayer.name, status = status.name, loggedAt = now))
c.writer.delete(c.db.bookmarks(), bookmark)         // حذف منطقي (tombstone)
```
- المعرّفات **حتمية** حيث أمكن (مثل `"2026-11-04_FAJR"`) فلا تتكرر السجلات عند المزامنة.
- القراءة بـ Flow: `c.db.prayerLogs().observeDay(date)`.
- كل الصفوف المحذوفة تبقى بـ `deleted=true` وتُستبعد من الاستعلامات.
- التاريخ دائمًا نصي ISO: `LocalDate.toString()` (`"2026-11-04"`) أو `dayKey()`.
- اليوم الميلادي هو حدّ السجلات (ليس يوم المغرب) — قرار موثّق.

### الإعدادات
`c.settings.observe(Settings.QURAN_FONT_SIZE)`، `c.settings.set(Settings.THEME_MODE, "dark")`. مفاتيح الإشعارات في `Settings.NOTIF_*`، ولكل صلاة `Settings.notifAdhan("FAJR")`. للإعدادات الخاصة بالجهاز فقط استخدم `c.localPrefs`.
عرّف مفاتيح إضافية في ملفات ميزتك: `val X = BoolKey(SettingScope.USER, "my_key", true)`.

### التذكيرات
- `c.reminders.schedule(Reminder(key, triggerAtMillis, channelId, title, text, route))`، `cancelByPrefix("prayer:")`.
- كل ميزة تملك كائن `…Reminders.rescheduleAll(c)` (ملفاته جاهزة كهياكل في `feature/*/reminders/`) ويجب أن:
  1) يقرأ إعدادات التفعيل/الأوقات،  2) يلغي مفاتيح ميزته القديمة `cancelByPrefix`،  3) يجدول القادم (حتى 3 أيام)، بمفاتيح ثابتة، 4) لا ينتج تكرارًا.
- استدعِ `c.requestReschedule()` بعد أي تغيير يؤثر على الأوقات.
- `route` يُفتح بالنقر: استخدم `Routes.*`.

### التنقل
- كل ميزة تسجّل رسمًا باسم `Routes.<X>` ووجهة ابتدائية `Routes.<X>_HOME` (الهياكل في `feature/*/…Navigation.kt` — استبدل محتواها).
- الشريط السفلي يظهر فقط في `Routes.TAB_ROOTS`؛ بقية الشاشات (القارئ، الإعدادات...) ملء الشاشة.
- تسجيل مسارات القرآن: `Routes.QURAN_READER_PATTERN` (وسائطه Int، `-1` = غير محدد)، `Routes.QURAN_PLANS_PATTERN`.
- تسجيل مسارات الأذكار: `Routes.ADHKAR_CATEGORY_PATTERN` (`categoryId` String، `start` Int).

## 3. تقسيم المسؤوليات
| الميزة | المجلد | تملك |
|---|---|---|
| القرآن | `feature/quran` | المصحف، الفهارس، العلامات، الأوراد، التمرير، الصوت، التفسير، البحث، المشاركة + `WirdReminders` |
| الأذكار | `feature/adhkar` | الأقسام، البطاقات، العداد، المفضلة، البحث + `AdhkarReminders` |
| الصلاة | `feature/prayer` | تسجيل الصلوات، السنن، المواقيت، القبلة، الأذان + `PrayerReminders` + `AdhanPlayer` |
| المهام/التقويم/الإحصاء/الرئيسية | `feature/tasks`, `feature/calendar`, `feature/journey`, `feature/home` | + `TaskReminders`, `DayEndReminders`, `EventReminders` |
| الإعدادات | `feature/settings` | الإعدادات، الحساب، الإشعارات، التحديث، onboarding، FCM (`YaqeenMessagingService`) |

الربط بين الميزات يتم عبر **Room/Settings** (مثلًا: الرئيسية تقرأ `db.prayerLogs()` مباشرة) وليس عبر استدعاء شاشات بعضها.

## 4. البناء والاختبار
- البناء: `powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 :app:compileDebugKotlin` (له قفل mutex يمنع بناءين معًا، وقد يستغرق دقائق؛ انتظر دوره).
- مسار المشروع **ASCII**: `C:\Users\hp\Desktop\YAQEEN` (مترجم Kotlin يفسد المسارات العربية).
- أخطاء ملفات الميزات الأخرى قد تظهر أثناء عملهم؛ ركّز على أخطاء ملفاتك فقط (`app/src/main/java/app/yaqeen/feature/<feature>/…`).
- اكتب اختبارات JVM وحدة (JUnit) لأي منطق خالص (حسابات، قواعد) في `app/src/test/java/app/yaqeen/...`. تشغيلها: `tools/gradle.ps1 :app:testDebugUnitTest --tests "app.yaqeen.feature.<x>.*"`.
- لا تدّعِ أن وظيفة تعمل إلا بعد تشغيل اختبارها/بنائها فعليًا؛ وثّق ما لم يكتمل بصدق في تقريرك.
