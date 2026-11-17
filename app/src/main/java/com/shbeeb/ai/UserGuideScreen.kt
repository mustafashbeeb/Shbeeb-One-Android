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
        "افتح «المساعدة»، واختر المحرك من القائمة الصغيرة. افتح «الإعدادات» وجهّز المحرك. إذا كان سحابياً احفظ مفتاح API واختبر الوصول للنموذج، ثم اكتب سؤالك واضغط «إرسال». ظهور «مفتاح محفوظ» أو «فحص ناجح» لا يضمن وجود رصيد للمحادثة."
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
        "افتح «المساعدة» واختر محرّكاً مجهّزاً، ثم اضغط «بدء محادثة صوتية». بعد موافقتك على إذن الميكروفون اسأل بالعربي، وانتظر الرد الصوتي؛ بعدها يحاول التطبيق الاستماع للسؤال التالي تلقائياً. لإيقاف كل ذلك اضغط «إيقاف المحادثة». تظل ميزة «إملاء» القديمة للرسالة الفردية. محرك نموذج الهاتف يمكنه الإجابة دون إنترنت، لكن تحويل الصوت لنص يتبع خدمة أندرويد وقد يحتاج اتصالاً أو حزمة لغة عربية محلية، والنطق يحتاج صوتاً عربياً مثبتاً. الوضع يعمل فقط والشاشة مفتوحة؛ قد تتطلب بعض الأجهزة بدءاً يدوياً جديداً بعد الصمت."
    ),
    HelpTopic(
        "5. ذكاء الهاتف بدون إنترنت",
        "من القائمة اختر «ذكاء الهاتف»، ثم استورد نموذجاً مرخّصاً بصيغة .litertlm. جرّب فصله عن الإنترنت للتأكد. من إعدادات النموذج اختر «سريع» لردود قصيرة، أو «متوازن» للرد الطبيعي، أو «مفصّل» لردود أطول قد تكون أبطأ. يمكنك بدء جلسة نموذج جديدة لإفراغ سياقه دون حذف سجل المحادثة المحفوظ. تنبيه: هذه الخيارات لا ترفع مستوى معرفة نموذج Gemma 1B؛ أي معلومة مهمّة يجب التحقق منها، والنماذج الأكبر قد تستهلك RAM أكثر وتتوقف على الجهاز."
    ),
    HelpTopic(
        "6. ما الفرق بين Ollama وذكاء الهاتف؟",
        "«ذكاء الهاتف» يشغّل نموذج LiteRT-LM مستورداً إلى مساحة تطبيقك. أما Ollama فيحتاج برنامج Ollama ونموذجاً مثبتاً على كمبيوتر داخل نفس شبكة Wi-Fi؛ أدخل عنوان IPv4 المحلي مثل http://192.168.1.10:11434 واسم النموذج المثبت. لا تفتح خادم Ollama للعامة."
    ),
    HelpTopic(
        "7. الصور والفيديو والملفات",
        "قسم الصور: تعديل محلي أساسي بدون إنترنت، وتوليد صورة عبر OpenAI فقط بعد تأكيد الرسوم المحتملة. قسم الفيديو: يحوّل صورة واحدة لمقطع MP4 صامت عمودي مدته 8 ثوانٍ؛ ليس توليد فيديو AI. قسم الملفات يعاين ملفات نصية محدودة دون رفعها لأي خدمة."
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
        Text("دليل الاستخدام", fontSize = 24.sp, color = Color(0xFFF7CB78), fontWeight = FontWeight.Bold)
        Text(
            "اضغط على عنوان أي قسم لعرض الخطوات. هذا الدليل يشرح الوظائف الموجودة بالتطبيق وحدودها.",
            color = Color(0xFFB7C8D8), fontSize = 13.sp
        )
        guideItems.forEachIndexed { index, item ->
            Card(
                onClick = { expanded = if (expanded == index) -1 else index },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xD12A1A48))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title + if (expanded == index) "  ▴" else "  ▾",
                        color = Color(0xFFF7CB78), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (expanded == index) Text(item.body, color = Color.White, fontSize = 13.sp)
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
