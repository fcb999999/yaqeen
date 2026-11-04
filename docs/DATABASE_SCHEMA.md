# مخطط قاعدة البيانات — يقين (YAQEEN)

> المصدر الفعلي للمخطط: `app/src/main/java/app/yaqeen/data/local/Entities.kt` و`Daos.kt`، وملف Room المُصدَّر في `app/schemas/app.yaqeen.data.local.YaqeenDatabase/1.json`. هذا الملف شرح للقرارات.

## 1. المبدأ: Local-First
- **Room** هو مصدر الحقيقة على الجهاز. كل قراءة وكل كتابة تتم محليًا أولًا، ولا تتطلب شاشة اتصالًا بالسحابة.
- **Cloud Firestore** نسخة مزامنة فقط، تحت مسار المستخدم: `users/{uid}/{collection}/{docId}`.
- القرآن والأذكار والتفسير الميسر **بيانات ثابتة للقراءة فقط** في `assets/` وليست في Room (انظر ARCHITECTURE.md)، وهي مفصولة تمامًا عن بيانات المستخدم وبيانات العرض.

## 2. الحقول المشتركة لكل جدول قابل للمزامنة
| الحقل | المعنى |
|---|---|
| `id` | معرّف نصي. **حتمي** حيث أمكن (مثل `2026-11-04_FAJR`) حتى لا يتكرر السجل عند إعادة المزامنة؛ UUID للسجلات الحرة (علامات، مهام) |
| `updatedAt` | رقم من «ساعة هجينة» (HybridClock) تضمن تزايدًا صارمًا على الجهاز؛ يُستعمل لحل التعارض |
| `deleted` | شاهد حذف (tombstone)؛ لا حذف فعليًا حتى يصل الحذف لكل الأجهزة (تنظيف بعد 30 يومًا) |
| `dirty` | تعديل محلي لم يُؤكَّد من السحابة (حقل محلي فقط، لا يُرسل) |
| `opId` | معرّف فريد للعملية الأخيرة: يمنع إعادة تطبيق العملية نفسها، ويمنع تعليم سجل كنظيف إذا تغيّر أثناء الإرسال |
| `deviceId` | الجهاز الذي أجرى آخر تعديل؛ يكسر تعادل `updatedAt` بشكل حتمي |

## 3. الجداول والمجموعات (Collections)
| جدول Room | مجموعة Firestore | المفتاح | ملاحظات |
|---|---|---|---|
| `reading_progress` | `reading_progress` | `deviceId` | **سجل لكل جهاز** فلا تعارض كتابة؛ الموضع الحالي = الأحدث `lastReadAt` |
| `quran_bookmark_categories` | `quran_bookmark_categories` | UUID | تصنيفات العلامات |
| `quran_bookmarks` | `quran_bookmarks` | UUID | سورة/آية/صفحة، عنوان، ملاحظة، لون، تصنيف |
| `quran_plans` | `quran_plans` | UUID | الأوراد: المدى بأرقام الآيات العامة 1..6236 + الوحدة + المقدار اليومي + `progressAyahId` |
| `quran_plan_logs` | `quran_plan_logs` | `{planId}_{date}` | سجل يومي للورد |
| `quran_day_logs` | `quran_day_logs` | `date` | إجمالي القراءة اليومي |
| `adhkar_progress` | `adhkar_progress` | `{date}_{itemId}` | عدّاد الذكر لليوم |
| `adhkar_favorites` | `adhkar_favorites` | `{type}_{itemId}` | مفضلة وعلامات مرجعية للأذكار |
| `prayers_tracking` | `prayers_tracking` | `{date}_{PRAYER}` | الحالة: MOSQUE / HOME / NOT_PRAYED. **غياب السجل = غير مسجّلة** |
| `rawatib_tracking` | `rawatib_tracking` | `{date}_{sunnahId}` | السنن الرواتب |
| `custom_sunnah` | `custom_sunnah` | UUID | سنن يضيفها المستخدم |
| `daily_tasks` | `daily_tasks` | UUID | المهام: تكرار، أيام، ربط بقسم |
| `task_completions` | `task_completions` | `{taskId}_{date}` | الإنجاز اليدوي (المرتبطة بعبادة يُشتق إنجازها من سجل العبادة نفسه) |
| `calendar_events` | `calendar_events` | UUID | أحداث شخصية (المناسبات الإسلامية تُحسب ولا تُخزَّن) |
| `settings` | `user_settings` + `notification_settings` | `{scope}:{key}` | جدول واحد يُقسَّم إلى مجموعتين حسب `scope` |
| `achievement_statistics` | `achievement_statistics` | `date` | ملخص يومي مُشتق (ذاكرة مؤقتة)، السجلات الأصلية هي مصدر الحقيقة |
| — | `users/{uid}` | `uid` | ملف المستخدم الشخصي (وثيقة الجذر) |
| — | `app_versions/{version}` | الإصدار | عامة للقراءة، تُكتب من الخدمة الخلفية فقط |

## 4. قرارات مهمة
1. **التاريخ الميلادي هو حدّ اليوم** في كل السجلات (وليس غروب الشمس)؛ قرار مبسّط موثّق.
2. **لا Destructive Migration أبدًا**: تحديث التطبيق يحافظ على البيانات؛ أي تغيير في المخطط يحتاج ترحيلًا مكتوبًا واختبارًا.
3. **حالة «غير مسجّل»**: الإحصائيات تعدّ المسجَّل فقط ولا تحتسب غير المسجَّل تقصيرًا.
4. **الأمان**: القواعد في `firebase/firestore.rules` تفرض: المستخدم يصل لمساره فقط، و`uid` و`id` داخل الوثيقة يطابقان المسار، وتحديث بـ`updatedAt` أقدم يُرفض (Last-Writer-Wins على الخادم)، ولا حذف فعلي.
5. **تبديل الحساب على الجهاز نفسه**: تُستبدل بيانات الجهاز ببيانات الحساب الجديد (حتى لا تختلط)؛ بيانات الوضع المحلي (قبل أي تسجيل دخول) تُضم لأول حساب.
