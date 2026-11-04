# البناء والإصدار الآلي (GitHub Actions)

| الملف | متى يعمل | ماذا يفعل |
|---|---|---|
| `workflows/ci.yml` | كل دفع وطلب دمج | اختبارات الوحدة + بناء debug + اختبارات سكربت الإصدار. لا أسرار. |
| `workflows/release.yml` | دفع وسم `v*` | يبني APK موقّعًا، ينشر GitHub Release، ثم يكتب `app_versions` ويرسل FCM. |

## إعداد لمرة واحدة

### 1) مفتاح التوقيع (مهم جدًا: احتفظ بنسخة احتياطية منه)
المفتاح نفسه يجب أن يوقّع **كل** الإصدارات، وإلا يرفض أندرويد التحديث (والتطبيق نفسه يتحقق من تطابق التوقيع قبل التثبيت). فقدانه يعني عدم القدرة على تحديث المستخدمين.

```
keytool -genkeypair -v -keystore yaqeen-release.jks -alias yaqeen -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 yaqeen-release.jks        # (Windows PowerShell: [Convert]::ToBase64String([IO.File]::ReadAllBytes("yaqeen-release.jks")))
```
لا ترفع الملف `.jks` إلى المستودع. احفظه في مكان آمن خارجه.

### 2) الأسرار (Settings > Secrets and variables > Actions > New repository secret)
| السر | القيمة | إلزامي |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | ناتج base64 لملف `.jks` | نعم |
| `ANDROID_KEYSTORE_PASSWORD` | كلمة مرور المفتاح الرئيسي (store) | نعم |
| `ANDROID_KEY_ALIAS` | الاسم المستعار (مثل `yaqeen`) | نعم |
| `ANDROID_KEY_PASSWORD` | كلمة مرور المفتاح | نعم |
| `GOOGLE_SERVICES_JSON` | **محتوى** `google-services.json` | لتفعيل الحساب والمزامنة |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | **محتوى** مفتاح حساب الخدمة | لـ `app_versions` وFCM (تُتخطى الخطوة إن غاب) |

كيفية إنشاء مشروع Firebase والملفات أعلاه: راجع [`firebase/README.md`](../firebase/README.md).

### 3) اربط التطبيق بمستودعك
workflow الإصدار يمرّر `-Pyaqeen.githubRepo=<owner>/<name>` تلقائيًا فيعرف التطبيق المنشور من أين يتحقق من التحديثات (`BuildConfig.GITHUB_REPO`). للبناء المحلي ضع في `gradle.properties`: `yaqeen.githubRepo=owner/name`.
المستودع يجب أن يكون **عامًا** (يستعلم التطبيق واجهة GitHub بلا توكن).

## نشر إصدار
```
powershell -File tools/release.ps1 -Bump patch        # أو minor / major أو -Version 1.0.0 ، ويدعم -DryRun
# عدّل قسم الإصدار في CHANGELOG.md (هو نص الإصدار الذي يراه المستخدمون)
git add gradle.properties CHANGELOG.md
git commit -m "release: v0.1.1"
git tag v0.1.1
git push origin main v0.1.1
```
يتحقق workflow من أن الوسم يطابق `yaqeen.versionName` في `gradle.properties` ويفشل إن اختلفا.

### ما الذي يُنشر
- `app-release.apk` و`app-release.apk.sha256` ضمن GitHub Release، ونص الإصدار من `CHANGELOG.md` (قسم `## [x.y.z]`) مع سطر `SHA-256: ...`.
- التطبيق يقرأ المجموع من هذه الملفات، ويتحقق من SHA-256 ومن **توقيع** الـAPK (مطابقته لتوقيع التطبيق المثبّت) قبل تشغيل المثبّت.
- `app_versions/<الإصدار>` في Firestore + رسالة FCM إلى الموضوع `app_updates`.

## بيانات المستخدم عند التحديث
معرّف الحزمة نفسه (`app.yaqeen`) وقاعدة Room بلا ترحيل مدمّر، فتبقى بيانات المستخدم عند التحديث. عند تغيير المخطط يلزم ترحيل Room صريح.

## اختبار محلي بلا اعتماد
```
node --test tools/notify-release.test.mjs
node tools/notify-release.mjs --dry-run --version 0.1.1 --version-code 2 --apk-url https://github.com/o/r/releases/download/v0.1.1/app-release.apk --changelog "تجربة"
```

## ملاحظات
- أول سطر في `gradle.properties` يجب ألا يحوي BOM (يزيله workflow احتياطًا؛ ويكتبه `tools/release.ps1` بلا BOM).
- اختبارات الجهاز (androidTest) غير مشمولة في CI.
- إن لم تُضبط أسرار التوقيع يفشل الإصدار برسالة واضحة بدل نشر APK غير موقّع.
