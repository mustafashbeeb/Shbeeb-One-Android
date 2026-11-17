# تقرير تدقيق وإصلاح البناء — AnythingLLM + Shbeeb

**التاريخ:** 17 نوفمبر 2026

## النتيجة

عالجت مشكلات كانت تمنع بناء Android وإنتاج ملف قابل للتثبيت. اكتمل التحقق المحلي بنجاح: فحص TypeScript، واختبارات JavaScript، واختبارات Android الوحدوية، وAndroid lint، ثم إنشاء APK تجريبي. أضيفت إلى GitHub Actions خطوة تنتج حزمة ZIP صريحة تضم APK وتعليمات تثبيت عربية وبصمة SHA-256.

## ما أُصلح

- كان Room KAPT يحمّل `kotlin-metadata-jvm 2.2.0` رغم وجود metadata بصيغة `2.4.0` في اعتماديات المشروع. ثبّتُّ `2.4.21` على إعدادات KAPT فقط، وضبطتُ مترجم Kotlin على JDK 21 مع إبقاء bytecode المستهدف Android/Java 17. نجحت مهمة `kaptDebugKotlin` بعد ذلك.
- وحدات Android المنقولة كانت تشير إلى `MainActivity` وموارد `R` بأسماء الحزمة الأصلية، لا بنشاط React Native المستضاف وnamespace التطبيق الحالي. وُجّهت إلى `AnythingActivity` و`com.shbeeb.ai.R`.
- كان lint يرفض استدعاء WorkManager `ListenableFuture.await()` المقيّد لمجموعة المكتبة. استُبدل بـ`Future.get()` داخل `Dispatchers.IO` في الموضعين، فلا يجري الانتظار على خيط الواجهة.
- Gradle 8.14 كان يكتشف اعتماداً غير معلن بين نسخ خطوط React Native Vector Icons ومهام lint. أُضيف اعتماد صريح لمهام lint analysis/report.
- حُدّث `.gitignore` لتجاهل symlink `node_modules` ومخرجات ObjectBox المولدة أثناء البناء، حتى لا تدخل بالخطأ في commit.

## التحقق المنفذ محلياً

- `yarn typecheck`: ناجح.
- `yarn test --runInBand`: **6 مجموعات اختبارات، 74 اختباراً ناجحاً**.
- `:app:testDebugUnitTest` و`:app:lintDebug`: ناجحان.
- `:app:assembleDebug`: ناجح؛ أُنتج `app-debug.apk` بحجم **72,615,743 بايت**.
- فُحص ZIP محلياً، وتأكد تطابق APK داخله مع SHA-256 التالي:

```text
597c71b5802a01335a44e338b481038dbff8a44917eb30572b006a939e10e7ca
```

## حدود النسخة

هذه نسخة **Debug تجريبية** وليست إصداراً إنتاجياً أو موقّعاً للنشر في المتجر. لم يُجر اختبار تثبيت وتشغيل على هاتف فعلي بعد. قد تحتاج إلى إزالة نسخة موقعة بمفتاح آخر قبل تثبيت هذا APK. كما قد يعرض lint تحذيرات غير مانعة؛ نجاحه يعني عدم وجود أخطاء lint تمنع البناء، لا أن كل تحذير قد أُزيل.

## مصادر تقنية

- [AndroidX Room release notes](https://developer.android.com/jetpack/androidx/releases/room)
- [Kotlin Metadata JVM](https://kotlinlang.org/docs/metadata-jvm.html)
- [Kotlin 2.4.0 release notes](https://kotlinlang.org/docs/whatsnew24.html)
