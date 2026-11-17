package com.anythingllm

import android.os.Bundle
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate

/** Hosts the upstream AnythingLLM React Native app inside the Shbeeb One application. */
class AnythingActivity : ReactActivity() {
    override fun getMainComponentName(): String = "AnythingLLM"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        SharedHostReactActivityDelegate(this, mainComponentName)

    override fun onCreate(savedInstanceState: Bundle?) {
        // ReactActivity handles edge-to-edge; avoid restoring react-native-screens fragments.
        Log.i(TAG, "Starting embedded AnythingLLM React Native activity")
        try {
            super.onCreate(null)
        } catch (failure: Exception) {
            showStartupFailure(failure)
        } catch (failure: LinkageError) {
            showStartupFailure(failure)
        }
    }

    private fun showStartupFailure(failure: Throwable) {
        Log.e(TAG, "Synchronous React Native startup failed", failure)
        setContentView(TextView(this).apply {
            text = "تعذّر تشغيل واجهة AnythingLLM. تم تسجيل نوع الخطأ محلياً في سجل Android؛ لن يغلق هذا الخطأ واجهة Shbeeb One الرئيسية."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(23, 39, 35))
            setBackgroundColor(Color.rgb(245, 247, 244))
            setPadding(40, 40, 40, 40)
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Do not serialize a potentially large prompt draft through Binder.
        outState.clear()
    }

    private companion object {
        const val TAG = "ShbeebAnythingActivity"
    }
}
