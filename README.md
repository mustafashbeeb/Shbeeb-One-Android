# AnythingLLM + Shbeeb AI

نسخة تجريبية تجمع تطبيق **Shbeeb AI** العربي (Kotlin/Jetpack Compose) مع **AnythingLLM Mobile** (React Native) داخل مشروع Android واحد. تظل شاشة Shbeeb هي شاشة التشغيل؛ ومن صفحتها الرئيسية يمكن فتح تجربة AnythingLLM المدمجة.

> **الحالة: تجريبية وغير جاهزة للنشر.** يتطلب اختباراً فعلياً على جهاز Android قبل اعتماد الميزات أو سلامتها للإصدار.

## ما يتضمنه المشروع

- واجهة Shbeeb العربية التي تشمل المحادثة، إعداد المحركات، الأدوات المحلية، الصوت، الصور والمهام.
- شاشة AnythingLLM Mobile لمساحات العمل والمحادثة والملفات وPDF، وتكاملات النماذج التي يوفرها المصدر.
- محرك LiteRT-LM الخاص بـShbeeb ومحرك `llama.rn` الخاص بتجربة AnythingLLM؛ هما مساران منفصلان.
- إشعار المصدر والترخيص في [`LICENSE`](LICENSE) و[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). يتضمن المشروع شفرة AnythingLLM المرخصة بـGPL-3.0-or-later؛ راجع التزامات الترخيص قبل إعادة التوزيع.
- شرح الدمج والخصوصية والتوافق والقيود في [`docs/ANYTHINGLLM_INTEGRATION_AR.md`](docs/ANYTHINGLLM_INTEGRATION_AR.md).

## الخصوصية

- لم تُدرج مفاتيح توقيع أو `google-services.json` أو أسرار Firebase.
- إرسال Telemetry إلى Firebase أو خوادم AnythingLLM معطّل في هذه النسخة.
- البحث على الويب متوقف افتراضياً؛ إذا فعّله المستخدم، تُرسل عبارة البحث إلى You.com.
- ميزة تحديد الموقع الشبكي معطلة، ولا ترسل IP إلى خدمة خارجية.
- استخدام مزود نموذج سحابي أو خادم يختاره المستخدم يرسل الطلبات إلى ذلك المزود. أما الاستدلال المحلي فيتطلب نموذجاً متوافقاً يختاره المستخدم.

## بناء APK مستقل للتجربة على Android

المتطلبات الحالية: JDK 17 أو أحدث (اختُبر إعداد البناء على JDK 21)، Node.js 20.19.5، Yarn Classic 1.22.22، Android SDK Platform 36، Build Tools 36.0.0، NDK 27.1.12297006 وCMake 3.22.1. إعدادات Android المحلية مثل `local.properties` يجب أن تبقى خارج Git.

```bash
yarn install --frozen-lockfile
./gradlew :app:assembleRelease
```

الناتج `app/build/outputs/apk/release/app-release.apk` يتضمن JavaScript bundle، لذلك لا يحتاج Metro ولا اتصالاً بالكمبيوتر. لأغراض الاختبار فقط، تستخدم هذه البنية مفتاح Android debug الافتراضي؛ لا تستخدمها للنشر التجاري. إذا أردت التطوير الحي عبر Metro، شغّل `yarn start` وثبّت نسخة Debug منفصلة. يقتصر ABI المهيأ حالياً على `arm64-v8a`، ولا تُضمّن أوزان نموذج محلي في APK.

## تحقق التطوير

بعد تجهيز البيئة، نفّذ `yarn typecheck`, `yarn test --runInBand`, `yarn lint`, ثم أمر Gradle أعلاه. ملفات CI موجودة في `.github/workflows/android.yml` وتبني APK مستقلاً وترفعه ضمن artifact باسم `anythingllm-and-shbeeb-standalone-package`.

## تنبيهات قبل النشر

1. اختبر فتح شاشة AnythingLLM، إعداد مساحة عمل ونموذج، استيراد PDF، وتشغيل المحركات على جهاز حقيقي.
2. أضف ترجمة عربية لتجربة AnythingLLM؛ القواميس المنقولة لا تشمل العربية حالياً.
3. راجع صلاحيات Android وسلوك كل SDK، وقيّد اتصالات HTTP الواضح قبل الإصدار العام عند الإمكان.
4. راجع GPL-3.0-or-later وجميع تراخيص الاعتماديات، بما فيها المكونات التي قد تكون غير مفتوحة المصدر.
5. جهّز مفاتيح توقيع يملكها ناشر التطبيق، ونفّذ مراجعة أمن وخصوصية قبل توزيع Release.
