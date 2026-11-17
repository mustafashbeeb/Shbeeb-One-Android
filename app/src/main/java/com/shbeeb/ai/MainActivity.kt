package com.shbeeb.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Android entry point for the rebuilt, assistant-first Shbeeb One interface. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShbeebOneApp() }
    }
}
