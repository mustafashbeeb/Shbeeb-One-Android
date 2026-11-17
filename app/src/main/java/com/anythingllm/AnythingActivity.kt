package com.anythingllm

import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate

/** Hosts the upstream AnythingLLM React Native app inside the Shbeeb application. */
class AnythingActivity : ReactActivity() {
    override fun getMainComponentName(): String = "AnythingLLM"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        SharedHostReactActivityDelegate(this, mainComponentName)

    override fun onCreate(savedInstanceState: Bundle?) {
        // ReactActivity handles edge-to-edge; avoid restoring react-native-screens fragments.
        super.onCreate(null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Do not serialize a potentially large prompt draft through Binder.
        outState.clear()
    }
}
