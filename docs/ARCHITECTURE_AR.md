# المعمارية وخطة التنفيذ

المبدأ: **واجهة مستقلة + قدرات مستقلة**. لا نمزج منطق الذكاء بواجهة المساعد الذكي.

```text
Android UI (Kotlin + Jetpack Compose)
     |
     +-- Hadeel / Chat UI
     +-- Engine status & selection UI
     +-- Tools UI / Settings / Permissions
     |
Future domain contracts
     +-- Local LLM backend (offline, model files installed by user)
     +-- Optional cloud adapters (explicit opt-in)
     +-- Optional voice STT / TTS
     +-- Optional Android actions (granular permissions)
     +-- Local private storage (encrypted where appropriate)
```

### مراحل العمل
- **1 / حالياً:** هيكل Android، تصميم عربي، شاشات قابلة للتنقل، CI build.
- **2:** قياس سرعة التطبيق والذاكرة على OPPO Find X5، وتثبيت محرك محلي صغير، واختبار العمل فعلياً بدون اتصال.
- **3:** دعم ملفات ومحادثات محلية وذاكرة يمكن التحكم فيها.
- **4:** الصوت المحلي والأوامر البسيطة وفق قيود نظام أندرويد.
- **5:** التحكم الاختياري بالتطبيقات والخدمات المتصلة، مع موافقات صريحة.
- **6:** الاستوديو الإبداعي والتكامل الاختياري مع الكمبيوتر والنماذج الخارجية.
- **7:** تحسين الأداء، اختبارات أمان، تراخيص، تجهيز نسخة تجارية.

### تنبيه معماري
لا نطلب AccessibilityService أو صلاحية الميكروفون أو وصولاً شاملاً للملفات إلا عند بناء ميزة تحتاجها. الوصول إلى تطبيقات أخرى ليس مضموناً؛ يجب اختبار كل وظيفة على إصدار أندرويد والجهاز المقصود.

### قرار تقني مبدئي
Kotlin + Jetpack Compose للواجهة. اختيار محرك LLM على الهاتف (مثل llama.cpp أو بديل مناسب) **مؤجل لاختبار الأداء والذاكرة**، وليس مفروضاً على مشروع الواجهة.
