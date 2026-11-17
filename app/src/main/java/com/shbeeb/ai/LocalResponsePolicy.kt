package com.shbeeb.ai

/**
 * Lightweight, testable controls for on-device LMs. Prompting can reduce some
 * hallucinations but does NOT guarantee accuracy, especially for 1B models.
 */
internal enum class LocalResponseMode(
    val id: String, val title: String, val maxOutputTokens: Int
) {
    FAST("fast", "سريع • رد قصير", 96),
    BALANCED("balanced", "متوازن • رد طبيعي", 192),
    DETAILED("detailed", "مفصّل • رد أطول", 384);

    companion object {
        fun fromId(id: String): LocalResponseMode =
            values().firstOrNull { it.id == id } ?: BALANCED
    }
}

internal object LocalResponsePolicy {
    fun buildPrompt(question: String, mode: LocalResponseMode): String {
        val trimmed = question.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 3000) {
            "اكتب سؤالاً لا يتجاوز 3000 حرف."
        }
        val guidance = when (mode) {
            LocalResponseMode.FAST ->
                "أجب بالعربية الفصحى مباشرةً بجملة أو جملتين."
            LocalResponseMode.BALANCED ->
                "أجب بالعربية الفصحى مباشرةً وبوضوح، مع خطوات قليلة عند الحاجة."
            LocalResponseMode.DETAILED ->
                "أجب بالعربية الفصحى بشكل منظم، مع تفاصيل نافعة فقط."
        }
        // Keep guidance short to limit extra prefill latency on CPU-only devices.
        return "$guidance إذا لم تعرف فقل ذلك ولا تخمّن. إذا كان السؤال غامضاً، اطلب توضيحاً.\nالسؤال: $trimmed"
    }
}
