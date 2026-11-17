# جدوى التوليد المحلي على الهاتف — Shbeeb One

**الجهاز المستهدف:** OPPO Find X5 بمعالج Snapdragon 888 وذاكرة فعلية 8GB، ومساحة تخزين حرة تقارب 183GB بحسب صورة المستخدم. لا أتعامل مع الذاكرة الافتراضية على أنها RAM فعلية، ولا أساوي قياس خادم أو هاتف أحدث بأداء هذا الجهاز.

## الصور: مسار تنفيذ فعلي

- أُضيفت شفرة المحرك المفتوح [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) (MIT) وGGML (MIT) كـsubmodules مثبتة على commits محددة. نجح محلياً بناء APK Release لـAndroid `arm64-v8a` باستخدام NDK `27.1.12297006` وCMake `3.22.1`؛ يحتوي على المكتبة `libshbeeb_media.so` وحزمة React Native. كما اجتاز Android tests وLint. هذا يثبت اكتمال البناء والربط البرمجي، لكنه **لا يثبت** تحميل النموذج أو توليد صورة فعلياً على OPPO Find X5.
- ملف النموذج المقترح هو `stable-diffusion-v1-5-pruned-emaonly-Q4_0.gguf` من مستودع second-state على Hugging Face. حجمه `1,566,768,416` بايت (نحو 1.57 GB)، وبصمته SHA-256: `b8944e9fe0b69b36ae1b5bb0185b3a7b8ef14347fe0fa9af6c64c4829022261f`.
- سير العمل داخل التطبيق يطلب موافقة المستخدم قبل تنزيل 1.57GB، ويتيح حفظ الملف إلى مجلد يختاره عبر Android SAF أو اختيار ملف سابق، ويتحقق من الحجم وبصمة التنزيل. عند مغادرة شاشة الصور يُلغى التوليد الجاري ويُحرر سياق المحرك. الأوزان لا تُحزم داخل APK.
- ترخيص شفرة المحرك MIT، لكن ترخيص الأوزان **CreativeML OpenRAIL-M** مستقل؛ يظل على المستخدم/الموزع مراجعة شروط النموذج. النموذج مخصص للصور وليس نموذج محادثة، وفهمه للأوصاف العربية أقل من الإنجليزية.
- التطبيق يستخدم إعداداً محافظاً بدقة 512×512 و20 خطوة. لم تُقَس سرعة التنفيذ أو الذاكرة أو حرارة Snapdragon 888؛ يلزم اختبار الهاتف قبل وصف الميزة بالمستقرة أو السريعة. البناء الأولي هنا CPU، لذا قد يكون بطيئاً على الهاتف.

## الفيديو: لماذا لا نعد بوعد غير مضمون

- توجد أداة في التطبيق لإنشاء MP4 صامت محلياً من صورة/تحريك بسيط. هذه **ليست** توليد فيديو diffusion من وصف نصي، ويجب تسميتها بوضوح.
- بطاقة نموذج [Wan2.1-T2V-1.3B-GGUF](https://huggingface.co/off-grid-ai/Wan2.1-T2V-1.3B-GGUF) تذكر أن أصغر حزمة Q4 كاملة تبلغ نحو `4.725 GB` على التخزين، وتشمل مولّد الفيديو وملف UMT5 وVAE؛ ملف المولّد وحده لا يكفي.
- قياس ناشر النموذج لـQ4 المحسّن أُجري على بطاقة NVIDIA RTX PRO 6000 Blackwell لخادم، مع دقة 832×480 و65 إطاراً و20 خطوة. سجل نحو `7.078 GiB` تخصيص GPU ذروة. هذه **ليست** نتيجة Android؛ ولا يجوز اعتبارها دليلاً على أداء الهاتف. الذاكرة الفعلية لهاتف المستخدم 8GB مشتركة بين النظام والتطبيقات، لذلك لا أعرض Wan كميزة احترافية مضمونة على Snapdragon 888، ولا أحمّل نموذج فيديو بهذا الحجم تلقائياً.
- مشروع Qualcomm Neodragon يعرض نتائج على منصات أحدث، لكن الشفرة العامة ليست تطبيق Android جاهزاً، ولا يثبت توافقاً/سرعة على Find X5. كما أن On-device Sora يتطلب iPhone 15 Pro وiOS ولا ينطبق على Android.

## القرار العملي

1. رفع نسخة البناء إلى CI والتحقق من APK وZIP وبصمة SHA-256، بعد نجاح التحقق المحلي.
2. تسمية محرر الفيديو المحلي بدقة «تحريك/إنشاء MP4 من صورة» وعدم تسويقه كتوليد فيديو ذكاء اصطناعي.
3. تأجيل توليد فيديو diffusion محلي على الهاتف إلى أن يظهر مسار متوافق مع Snapdragon 888 ويمكن اختباره فعلياً دون تجاوز الذاكرة أو إحداث إغلاق للتطبيق.
4. اختبار النسخة على OPPO Find X5: فتح النموذج من SAF، توليد صورة دون اتصال، الإلغاء، حفظ PNG، قياس وقت التوليد والذاكرة والحرارة، ثم ضبط الإعدادات بناءً على القياس. هذا الاختبار لم يحدث بعد.

## مصادر

1. Google Research، MobileDiffusion: https://research.google/blog/mobilediffusion-rapid-text-to-image-generation-on-device/
2. البحث الأصلي MobileDiffusion: https://arxiv.org/abs/2311.16567
3. Local Dream، توافق الأجهزة: https://ld-guide.chino.icu/compatibility
4. Local Dream، بدء الاستخدام والنماذج: https://ld-guide.chino.icu/getting-started
5. Local Dream، الترخيص: https://github.com/xororz/local-dream/blob/master/LICENSE
6. MNN، محرك الصور Android: https://mnn-docs.readthedocs.io/en/latest/transformers/diffusion.html
7. MNN، المستودع والترخيص: https://github.com/alibaba/MNN
8. stable-diffusion.cpp، المصدر والترخيص: https://github.com/leejet/stable-diffusion.cpp
9. stable-diffusion.cpp، بناء Android NDK/OpenCL: https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/build.md
10. SD 1.5 Q4 GGUF: https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF
11. ترخيص أوزان SD 1.5: https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF/blob/main/LICENSE
12. Wan2.1 Q4 GGUF وقياسات الناشر: https://huggingface.co/off-grid-ai/Wan2.1-T2V-1.3B-GGUF
13. Qualcomm AI Research، Neodragon: https://qualcomm-ai-research.github.io/neodragon/
14. Neodragon، الشفرة: https://github.com/Qualcomm-AI-research/neodragon
15. On-device Sora، الشفرة ومتطلبات iOS: https://github.com/eai-lab/On-device-Sora
