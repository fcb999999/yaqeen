# عملية الإصدار والتحديث — يقين

## 1. مفتاح التوقيع (مهم جدًا)
- كل إصدارات APK يجب أن تُوقَّع **بالمفتاح نفسه** وإلا رفض Android التحديث فوق النسخة المثبتة.
- أُنشئ مفتاح الإصدار خارج المستودع:
  - الملف: `C:\Users\hp\yaqeen-keystore\yaqeen-release.jks`
  - الإعدادات (وكلمة المرور): `C:\Users\hp\.yaqeen\keystore.properties` (يقرؤها `app/build.gradle.kts` تلقائيًا؛ ولا يُرفع للمستودع أبدًا)
- **خذ نسخة احتياطية من المجلدين الآن** (USB/تخزين آمن). فقدان المفتاح = استحالة تحديث المستخدمين الحاليين.
- بصمات شهادة الإصدار (للتسجيل في Firebase):
  - SHA-1: `99:9B:EC:70:38:C7:70:C9:57:CC:88:56:E5:99:7C:8C:9D:B3:A2:A0`
  - SHA-256: `8A:C2:16:D5:2D:05:11:2F:C3:37:A9:B8:BD:A0:B4:17:B7:82:BB:7C:70:E5:B3:87:43:3C:63:FA:51:B0:C4:46`
- بصمات شهادة Debug على هذا الجهاز (لتسجيل الدخول بنسخة Debug): SHA-1 `85:1A:0F:96:A2:FB:84:92:0A:A0:D7:E5:45:60:9F:F0:BC:B2:68:CA`.

## 2. بناء إصدار محليًا
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 :app:assembleRelease
# الناتج: app/build/outputs/apk/release/app-release.apk  (موقّع + R8)
```
التحقق من التوقيع: `apksigner verify --print-certs app-release.apk` (من build-tools).

## 3. ترقيم الإصدارات
- `gradle.properties`: `yaqeen.versionName` (مثل 0.1.0) و`yaqeen.versionCode` (عدد صحيح يزيد دائمًا).
- التسمية الدلالية: MAJOR.MINOR.PATCH. وسم Git: `v0.1.0`.
- سجل التغييرات في `CHANGELOG.md` (قسم لكل إصدار) وهو ما يظهر للمستخدم في شاشة التحديث.

## 4. النشر عبر GitHub Releases (التوزيع المباشر)
1. حدّث `CHANGELOG.md` وأرقام الإصدار، ثم commit.
2. أنشئ الوسم وادفعه: `git tag v0.1.0 && git push origin v0.1.0`.
3. يعمل `.github/workflows/release.yml` تلقائيًا:
   - يبني APK موقّعًا (الأسرار: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `GOOGLE_SERVICES_JSON`)،
   - ينشئ GitHub Release بالـAPK وملف البصمة `app-release.apk.sha256` ونص التغييرات،
   - يشغّل `tools/notify-release.mjs` (سر `FIREBASE_SERVICE_ACCOUNT_JSON`): يكتب `app_versions/<version>` في Firestore ويرسل إشعار FCM إلى الموضوع `app_updates`.
4. التطبيق: إشعار «إصدار جديد» ⇐ شاشة التحديث (سجل التغييرات + «تحديث الآن») ⇐ تنزيل ⇐ **تحقق من SHA-256 ومن مطابقة توقيع الـAPK لتوقيع التطبيق** ⇐ التثبيت. بيانات المستخدم محفوظة (نفس الحزمة، ولا ترحيل مدمّر).
- المستودع يجب أن يكون **عامًا** حتى يتحقق التطبيق من الإصدار الأحدث دون مصادقة، أو يُستبدل التحقق بقراءة `app_versions` من Firestore.
- ضبط المستودع في التطبيق: `yaqeen.githubRepo=owner/name` في `gradle.properties` (أو `-Pyaqeen.githubRepo=...`).

## 5. النشر عبر Google Play (بديل)
- ارفع **AAB** (`:app:bundleRelease`) وفعّل Play App Signing؛ ولا تستخدم آلية التثبيت الذاتي (التطبيق يتعرّف على مصدر Play ويوجّه المستخدم للمتجر).
- يمكن لاحقًا إضافة Play In-App Updates API.

## 6. قائمة فحص قبل كل إصدار
- [ ] جميع اختبارات JVM خضراء (`:app:testDebugUnitTest`).
- [ ] بناء Release ناجح وموقّع، وفحص التوقيع.
- [ ] تثبيت فوق الإصدار السابق بنفس المفتاح والتأكد من بقاء البيانات (اختبار «تحديث مع الحفاظ على البيانات»).
- [ ] تشغيل بلا إنترنت، ثم مزامنة على جهازين.
- [ ] تحديث `CHANGELOG.md`.
