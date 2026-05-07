package com.artembolotov.twinkey

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.artembolotov.twinkey.ui.theme.TwinKeyTheme
import com.artembolotov.twinkey.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        // Prevent IME show/hide from resizing the window so ModalBottomSheet anchors
        // are not recalculated during a swipe gesture. imePadding() handles content offset.
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContent {
            TwinKeyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WelcomeScreen()
                }
            }
        }
    }
}
