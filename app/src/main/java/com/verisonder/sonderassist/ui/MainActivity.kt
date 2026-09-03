package com.verisonder.sonderassist.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.verisonder.sonderassist.ui.theme.SonderAssistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SonderAssistTheme {
                AppRoot(this)
            }
        }
    }
}
