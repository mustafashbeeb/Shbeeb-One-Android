#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <string>
#include <vector>

#include "stable-diffusion.h"

namespace {
struct EngineHandle {
    sd_ctx_t* context = nullptr;
};

void throw_java(JNIEnv* env, const char* type, const std::string& message) {
    jclass klass = env->FindClass(type);
    if (klass != nullptr) {
        env->ThrowNew(klass, message.c_str());
        env->DeleteLocalRef(klass);
    }
}

std::string last_error(const char* fallback) {
    const char* error = sd_get_last_error();
    return error != nullptr && error[0] != '\0' ? std::string(error) : std::string(fallback);
}

bool set_string(sd_opt_t* opts, const char* key, const char* value) {
    return sd_opt_set_string(opts, key, value) == SD_STATUS_OK;
}

bool set_int(sd_opt_t* opts, const char* key, int64_t value) {
    return sd_opt_set_int(opts, key, value) == SD_STATUS_OK;
}

struct ProgressCallback {
    JNIEnv* env = nullptr;
    jobject listener = nullptr;
    jmethodID method = nullptr;
};

void report_progress(const sd_generation_progress_t* progress, void* opaque) {
    auto* callback = static_cast<ProgressCallback*>(opaque);
    if (callback == nullptr || callback->env == nullptr || callback->listener == nullptr || callback->method == nullptr || progress == nullptr) {
        return;
    }
    const jlong completed = static_cast<jlong>(std::max<int64_t>(0, progress->completed));
    const jlong total = static_cast<jlong>(std::max<int64_t>(0, progress->total));
    callback->env->CallVoidMethod(callback->listener, callback->method, completed, total);
    if (callback->env->ExceptionCheck()) {
        callback->env->ExceptionClear();
    }
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_shbeeb_ai_LocalDiffusionEngine_nativeCreate(JNIEnv* env, jobject, jint file_descriptor) {
    if (file_descriptor < 0) {
        throw_java(env, "java/lang/IllegalArgumentException", "ملف النموذج غير صالح.");
        return 0;
    }

    const std::string model_path = "/proc/self/fd/" + std::to_string(file_descriptor);
    sd_ctx_opt_t* options = sd_ctx_opt_create();
    if (options == nullptr) {
        throw_java(env, "java/lang/OutOfMemoryError", "تعذر إنشاء إعدادات محرك الصور.");
        return 0;
    }

    const bool configured = set_string(options, "model_path", model_path.c_str()) &&
                            set_int(options, "n_threads", 4) &&
                            sd_opt_set_bool(options, "enable_mmap", true) == SD_STATUS_OK;
    if (!configured) {
        const std::string error = last_error("تعذر إعداد النموذج المحلي.");
        sd_opt_free(options);
        throw_java(env, "java/lang/IllegalArgumentException", error);
        return 0;
    }

    sd_ctx_t* context = nullptr;
    const sd_status_t status = sd_ctx_create(options, &context);
    sd_opt_free(options);
    if (status != SD_STATUS_OK || context == nullptr) {
        const std::string error = last_error("تعذر تحميل نموذج الصور. تحقق من الملف وتوافقه.");
        if (context != nullptr) sd_ctx_free(context);
        throw_java(env, "java/lang/IllegalStateException", error);
        return 0;
    }

    auto* handle = new (std::nothrow) EngineHandle();
    if (handle == nullptr) {
        sd_ctx_free(context);
        throw_java(env, "java/lang/OutOfMemoryError", "لا توجد ذاكرة كافية لفتح محرك الصور.");
        return 0;
    }
    handle->context = context;
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_shbeeb_ai_LocalDiffusionEngine_nativeGenerate(
    JNIEnv* env,
    jobject,
    jlong native_handle,
    jstring prompt_value,
    jstring negative_prompt_value,
    jint width,
    jint height,
    jint steps,
    jlong seed,
    jobject progress_listener) {
    auto* handle = reinterpret_cast<EngineHandle*>(native_handle);
    if (handle == nullptr || handle->context == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "محرك الصور غير محمّل.");
        return nullptr;
    }
    if (prompt_value == nullptr) {
        throw_java(env, "java/lang/IllegalArgumentException", "اكتب وصف الصورة أولاً.");
        return nullptr;
    }

    const char* prompt_chars = env->GetStringUTFChars(prompt_value, nullptr);
    const char* negative_chars = negative_prompt_value == nullptr ? nullptr : env->GetStringUTFChars(negative_prompt_value, nullptr);
    if (prompt_chars == nullptr || (negative_prompt_value != nullptr && negative_chars == nullptr)) {
        if (prompt_chars != nullptr) env->ReleaseStringUTFChars(prompt_value, prompt_chars);
        throw_java(env, "java/lang/OutOfMemoryError", "تعذر قراءة وصف الصورة.");
        return nullptr;
    }

    sd_img_gen_opt_t* options = sd_img_gen_opt_create();
    sd_result_t* result = nullptr;
    if (options == nullptr) {
        env->ReleaseStringUTFChars(prompt_value, prompt_chars);
        if (negative_chars != nullptr) env->ReleaseStringUTFChars(negative_prompt_value, negative_chars);
        throw_java(env, "java/lang/OutOfMemoryError", "تعذر إنشاء إعدادات التوليد.");
        return nullptr;
    }

    bool configured = set_string(options, "prompt", prompt_chars) &&
                       set_int(options, "width", width) &&
                       set_int(options, "height", height) &&
                       set_int(options, "seed", seed) &&
                       set_int(options, "sample.steps", steps) &&
                       set_string(options, "sample.method", "euler");
    if (configured && negative_chars != nullptr && negative_chars[0] != '\0') {
        configured = set_string(options, "negative_prompt", negative_chars);
    }
    env->ReleaseStringUTFChars(prompt_value, prompt_chars);
    if (negative_chars != nullptr) env->ReleaseStringUTFChars(negative_prompt_value, negative_chars);

    if (!configured) {
        const std::string error = last_error("إعدادات توليد الصورة غير صالحة.");
        sd_opt_free(options);
        throw_java(env, "java/lang/IllegalArgumentException", error);
        return nullptr;
    }

    ProgressCallback callback;
    callback.env = env;
    if (progress_listener != nullptr) {
        callback.listener = env->NewGlobalRef(progress_listener);
        jclass listener_class = env->GetObjectClass(progress_listener);
        callback.method = env->GetMethodID(listener_class, "onProgress", "(JJ)V");
        env->DeleteLocalRef(listener_class);
    }

    sd_cancel_generation(handle->context, SD_CANCEL_RESET);
    sd_set_generation_progress_callback(handle->context, report_progress, &callback);
    const sd_status_t status = sd_generate_image(handle->context, options, &result);
    sd_set_generation_progress_callback(handle->context, nullptr, nullptr);

    if (callback.listener != nullptr) env->DeleteGlobalRef(callback.listener);
    sd_opt_free(options);
    if (status != SD_STATUS_OK || result == nullptr || sd_result_get_image_count(result) < 1) {
        const std::string error = last_error("فشل توليد الصورة محلياً.");
        sd_result_free(result);
        throw_java(env, "java/lang/IllegalStateException", error);
        return nullptr;
    }

    const sd_tensor_t* image = sd_result_get_image(result, 0);
    if (image == nullptr || sd_tensor_rank(image) != 3 || sd_tensor_dim(image, 0) != 3) {
        const std::string error = last_error("أعاد المحرك صيغة صورة غير متوقعة.");
        sd_result_free(result);
        throw_java(env, "java/lang/IllegalStateException", error);
        return nullptr;
    }
    const int64_t actual_width = sd_tensor_dim(image, 1);
    const int64_t actual_height = sd_tensor_dim(image, 2);
    const size_t byte_count = sd_tensor_byte_size(image);
    const auto* pixels = static_cast<const jbyte*>(sd_tensor_data(image));
    if (actual_width != width || actual_height != height || pixels == nullptr || byte_count != static_cast<size_t>(width) * static_cast<size_t>(height) * 3) {
        sd_result_free(result);
        throw_java(env, "java/lang/IllegalStateException", "حجم الصورة الناتجة غير متوافق مع إعدادات التطبيق.");
        return nullptr;
    }

    jbyteArray output = env->NewByteArray(static_cast<jsize>(byte_count));
    if (output != nullptr) {
        env->SetByteArrayRegion(output, 0, static_cast<jsize>(byte_count), pixels);
    }
    sd_result_free(result);
    return output;
}

extern "C" JNIEXPORT void JNICALL
Java_com_shbeeb_ai_LocalDiffusionEngine_nativeCancel(JNIEnv*, jobject, jlong native_handle) {
    auto* handle = reinterpret_cast<EngineHandle*>(native_handle);
    if (handle != nullptr && handle->context != nullptr) {
        sd_cancel_generation(handle->context, SD_CANCEL_ALL);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_shbeeb_ai_LocalDiffusionEngine_nativeDestroy(JNIEnv*, jobject, jlong native_handle) {
    auto* handle = reinterpret_cast<EngineHandle*>(native_handle);
    if (handle != nullptr) {
        if (handle->context != nullptr) sd_ctx_free(handle->context);
        delete handle;
    }
}
