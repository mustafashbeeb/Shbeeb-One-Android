package com.shbeeb.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class HelpTopic(val title: String, val body: String)

private val guideItems = listOf(
    HelpTopic(
        "1. كيف أبدأ المحادثة؟",
        "افتح «المساعد»، واختر المحرك من أعلى الشاشة. جهّز المحرك من «الإعدادات». إذا كان سحابياً احفظ مفتاح API؛ سيطلب التطبيق موافقتك قبل إرسال كل رسالة سحابية، وقد يستهلك ذلك رصيداً."
    ),
    HelpTopic(
        "2. استخراج وحفظ مفتاح OpenAI أو Gemini أو Claude",
        "اختر المحرك، ثم افتح «الإعدادات» واضغط «كيف آخذ المفتاح؟» لفتح موقع الشركة الرسمي. انسخ مفتاح API، ألصقه في خانة المحرك نفسه، واضغط «حفظ المفتاح». تختفي قيمته بعد الحفظ ويُشفّر داخل الهاتف. «حذف المحفوظ» يحذف النسخة المخزنة على الجهاز، ولا يلغي المفتاح الأصلي من موقع الشركة. إذا أردت إلغاءه نهائياً، ألغِه أيضاً من حساب الشركة."
    ),
    HelpTopic(
        "3. لماذا المفتاح موجود لكن لا يردّ المحرك؟",
        "الخطوة الأولى: اضغط «فحص اتصال المحرك». هذا فحص قراءة لبيانات النموذج ولا يولّد رسالة مدفوعة. إذا رفض المفتاح، راجع النوع والصلاحيات. إذا لم يجد النموذج، اختر نموذجاً متاحاً لحسابك. إذا ظهر 429 قد يكون حد طلبات أو نقص رصيد، وتحتاج مراجعة حساب API. اشتراك تطبيق الدردشة لا يعني اشتراك API."
    ),
    HelpTopic(
        "4. كيف أتكلم مع التطبيق بالصوت؟",
        "من شاشة «المساعد» اضغط زر الميكروفون بعد تجهيز المحرك. بعد منح إذن الميكروفون تحدث، ثم ينتظر التطبيق تحويل كلامك إلى نص ويرد بصوت؛ هذه جولات متتابعة وليست استماعاً مستمراً أو حديثاً متزامناً. أوقف الجلسة من زر الإيقاف. قد تحتاج خدمة التعرف على الكلام من Android إلى الإنترنت أو حزمة عربية محلية، ويحتاج النطق إلى صوت عربي مثبت. تعمل الجلسة ما دامت الشاشة مفتوحة."
    ),
    HelpTopic(
        "5. محرك LiteRT-LM المحلي بدون إنترنت",
        "من «الإعدادات» اختر محرك LiteRT، ثم حدّد ملف .litertlm محلياً. عند أول استخدام ينشئ التطبيق نسخة تشغيل داخلية بامتداد صحيح ويحتاج مساحة إضافية تقارب حجم النموذج + 128 MB؛ يبقى الأصل المشترك دون تغيير. يختلف GGUF عن .litertlm ولا يعمل مع LiteRT-LM. جرّب رداً فعلياً، فاختيار الملف لا يضمن توافقه."
    ),
    HelpTopic(
        "6. ما الفرق بين Ollama وLiteRT-LM؟",
        "LiteRT-LM يشغّل ملف .litertlm متوافقاً على الهاتف. أما Ollama فيتصل بخادم على كمبيوتر داخل شبكة Wi-Fi؛ أدخل عنوان IPv4 واسم النموذج المثبت. يستطيع Ollama استيراد GGUF، لكن ذلك لا يجعله ملف LiteRT ولا يضمن مشاركة التخزين بين التطبيقات. لا تفتح خادم Ollama للعامة."
    ),
    HelpTopic(
        "7. الصور والفيديو والملفات",
        "الاستوديو: توليد الصور محلياً عبر Stable Diffusion 1.5 بعد توفير ملف النموذج، بدقة 512×512؛ أما الفيديو فيحوّل صورة واحدة إلى MP4 صامت عمودي مدته 8 ثوانٍ، وليس توليد فيديو AI. قسم الملفات يعاين نصاً محلياً محدوداً ولا يرفعه إلى الذكاء."
    ),
    HelpTopic(
        "8. الخصوصية والتخزين والحذف",
        "مفاتيح الخدمات السحابية محفوظة مشفرة في Android Keystore ويمكن حذفها لكل محرك. حفظ المحادثات اختياري ومغلق افتراضياً؛ تخزين الرسائل المحلي غير مشفر تشفيراً خاصاً. يمكنك حذف السجل من الإعدادات وتصدير المحادثة بنفسك. لا ترسل مفاتيحك أو صورها في الرسائل أو الدعم."
    )
)

@Composable
internal fun UserGuideScreen() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("دليل الاستخدام", fontSize = 24.sp, color = OnePrimary, fontWeight = FontWeight.Bold)
        Text(
            "اضغط على عنوان أي قسم لعرض الخطوات. هذا الدليل يشرح الوظائف الموجودة بالتطبيق وحدودها.",
            color = OneMuted, fontSize = 13.sp
        )
        guideItems.forEachIndexed { index, item ->
            Card(
                onClick = { expanded = if (expanded == index) -1 else index },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OneSurface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title + if (expanded == index) "  ▴" else "  ▾",
                        color = OnePrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (expanded == index) Text(item.body, color = OneInk, fontSize = 13.sp)
                }
            }
        }
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/gemini-api/docs/api-key"))
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("دليل Google الرسمي لمفاتيح Gemini") }
    }
}
