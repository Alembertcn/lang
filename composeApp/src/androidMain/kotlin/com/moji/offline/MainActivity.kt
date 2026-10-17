package com.moji.offline

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Application context for platform services that are not Compose-scoped. */
object AppContext {
    lateinit var context: Context
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppContext.context = applicationContext
        setContent { App() }
    }
}
