# إشعارات المصادر والتراخيص الخارجية

## AnythingLLM Mobile

يتضمن هذا المستودع شفرة مشتقة من [AnythingLLM Mobile](https://github.com/Mintplex-Labs/anythingllm-mobile)، عند commit المصدر `fef1fe7f9fdfba7895f68aa2b4d332ae510ddc3`. المشروع الأصلي مرخص بموجب **GNU GPL الإصدار 3 أو أي إصدار أحدث**. راجع ملف [`LICENSE`](LICENSE) وإشعارات الترخيص المنقولة مع المصدر. اسم AnythingLLM باقٍ هنا لإسناد التقنية، بينما اسم التطبيق المركب هو **Shbeeb One**.

## stable-diffusion.cpp وGGML

يتضمن بناء Android محرك [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) عند commit `532d2c15c430a785a712b2d94281f04e733008a4`، بترخيص **MIT**. يتضمن المحرك مكتبة [GGML](https://github.com/leejet/ggml) عند commit `eeda9ec48fc84a3df0bb9dcc19ae9053075a2539`، بترخيص **MIT**. ملفات الرخص الأصلية محفوظة داخل المجلد `third_party/stable-diffusion.cpp` والـsubmodule المتداخل `ggml`؛ يجب الإبقاء عليها عند إعادة التوزيع.

## أوزان Stable Diffusion 1.5

لا تتضمن الشفرة أو APK أوزان نموذج الصورة. يمكن للمستخدم تنزيل ملف `stable-diffusion-v1-5-pruned-emaonly-Q4_0.gguf` من [مستودع second-state على Hugging Face](https://huggingface.co/second-state/stable-diffusion-v1-5-GGUF)، حجمه `1,566,768,416` بايت وبصمة SHA-256 هي `b8944e9fe0b69b36ae1b5bb0185b3a7b8ef14347fe0fa9af6c64c4829022261f`. ترخيص الأوزان **CreativeML OpenRAIL-M** منفصل عن تراخيص الشفرة؛ راجع بطاقة النموذج ونص الترخيص قبل الاستخدام أو المشاركة.

## إعدادات خاصة غير منقولة

لم تُدرج إعدادات Firebase الأصلية (`google-services.json`) ولا مفاتيح توقيع أو أسرار للنشر. تعطيل القياس عن بُعد في هذا البناء لا يمنع اتصالات يختارها المستخدم صراحةً، مثل تنزيل النموذج أو استخدام مزود سحابي.

## ملاحظات إعادة التوزيع

تُعدّل شفرة المصدر المدمجة وتُوزع وفق تراخيصها الخاصة؛ يلزم حفظ الإشعارات المتوافقة. لا يعني ترخيص الشفرة أن أوزان النماذج أو كل الاعتماديات تملك الترخيص نفسه. راجع الرخص الأصلية لكل مكوّن قبل نشر APK أو مشاركة أوزان نموذج.
