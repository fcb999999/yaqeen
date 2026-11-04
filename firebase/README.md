# إعداد Firebase لتطبيق يقين (مرة واحدة)

يعمل يقين كاملًا **بدون Firebase** (وضع محلي). Firebase مطلوب فقط لـ: تسجيل الدخول بحساب Google، والمزامنة بين الأجهزة، وإشعارات الإصدارات الجديدة (FCM).
بدون الملف `app/google-services.json` يُبنى التطبيق في الوضع المحلي ويعرض شاشة الحساب شرحًا بذلك.

## 1) إنشاء المشروع
1. افتح <https://console.firebase.google.com> وأنشئ مشروعًا جديدًا (يمكن تعطيل Google Analytics؛ التطبيق لا يستخدمه).
2. من **Build > Firestore Database** أنشئ قاعدة بيانات (وضع الإنتاج) في منطقة قريبة من مستخدميك.

## 2) تفعيل تسجيل الدخول بحساب Google
1. **Build > Authentication > Sign-in method** فعّل مزوّد **Google** وحدّد بريد الدعم.
2. هذا ينشئ تلقائيًا عميل "Web" يُستخدم من التطبيق عبر `default_web_client_id` (يولّده مكوّن google-services من الملف).

## 3) إضافة تطبيق أندرويد
أضف تطبيقًا واحدًا لكل معرّف حزمة تستخدمه:

| البناء | معرّف الحزمة |
|---|---|
| الإصدار (release) | `app.yaqeen` |
| التطوير (debug) | `app.yaqeen.debug` |

لكل تطبيق أضف بصمات الشهادة **SHA-1 و SHA-256** (Project settings > Your apps > Add fingerprint). بدونها يفشل تسجيل الدخول بحساب Google.

- **بصمة debug**: من جذر المشروع:
  `keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android`
- **بصمة release**: من مفتاح التوقيع الذي تنشر به:
  `keytool -list -v -keystore <مسار_المفتاح>.jks -alias <الاسم_المستعار>`
- إن رفعت التطبيق لاحقًا إلى Google Play مع Play App Signing فأضف أيضًا بصمات "App signing key" من Play Console.

## 4) ملف الإعداد
نزّل `google-services.json` وضعه في **`app/google-services.json`**. الملف **ممنوع رفعه** إلى المستودع (مدرج في `.gitignore`).
في GitHub يُخزَّن محتواه كسر `GOOGLE_SERVICES_JSON` ليكتبه workflow الإصدار وقت البناء (انظر `.github/README.md`).

> ملاحظة: ملف واحد يخدم التطبيقين (`app.yaqeen` و`app.yaqeen.debug`) إذا أضفتهما معًا إلى المشروع نفسه.

## 5) نشر قواعد الأمان
القواعد في `firebase/firestore.rules` (بيانات كل مستخدم تحت `users/{uid}` لصاحبها فقط، وLast-Writer-Wins، ولا حذف فعلي، و`app_versions` للقراءة فقط):

```
npm i -g firebase-tools
firebase login
firebase use <معرّف-المشروع>
firebase deploy --only firestore:rules,firestore:indexes --config firebase/firebase.json
```

## 6) إشعارات الإصدارات (FCM) ووثيقة الإصدار
- التطبيق يشترك تلقائيًا في الموضوع **`app_updates`** إن كان إعداد "إشعارات تحديثات التطبيق" مفعّلًا.
- عند نشر إصدار يرسل `tools/notify-release.mjs` رسالة بيانات (data فقط): `{type:"update", version, url}` ويكتب وثيقة `app_versions/<versionName>` (الحقول: `versionName, versionCode, publishedAt, changelog, apkUrl, sha256`). الكتابة تتم بـ Admin SDK فتتجاوز القواعد.
- ينشئ هذا السر: **Project settings > Service accounts > Generate new private key**، ثم يوضع **محتوى** الملف كسر `FIREBASE_SERVICE_ACCOUNT_JSON` في GitHub. لا ترفع الملف أبدًا (الأنماط `service-account*.json` و`*-firebase-adminsdk-*.json` مستثناة في `.gitignore`).
- تجربة بلا اعتماد: `node tools/notify-release.mjs --dry-run --version 1.0.0 --version-code 2 --apk-url https://github.com/<owner>/<repo>/releases/download/v1.0.0/app-release.apk`.

## 7) التحقق
1. ابنِ التطبيق بعد وضع `google-services.json` وشغّله، وافتح الإعدادات > حساب Google: يجب أن يظهر زر "تسجيل الدخول بحساب Google".
2. بعد الدخول، أضف بيانات (مثل تسجيل صلاة) وتحقق من ظهور الوثائق تحت `users/<uid>/prayers_tracking` في Firestore.
3. للتحقق من القواعد محليًا راجع `tools/rules-test`.

## استكشاف الأخطاء
| العَرَض | السبب المحتمل |
|---|---|
| "لا يوجد حساب Google على هذا الجهاز" | لم يُضف حساب Google في إعدادات الجهاز |
| فشل الدخول فور الضغط | بصمات SHA-1/SHA-256 ناقصة أو لمعرّف حزمة آخر، أو الملف قديم (نزّله بعد إضافة البصمات) |
| الشاشة تقول إن الحساب غير متاح | `app/google-services.json` غير موجود وقت البناء |
| رفض الكتابة في Firestore | القواعد غير منشورة أو الجهاز بتوقيت يخالف الخادم بفارق كبير |
