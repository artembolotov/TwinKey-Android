package com.artembolotov.twinkey

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.artembolotov.twinkey.ui.accounts.AccountsViewModel
import com.artembolotov.twinkey.ui.theme.TwinKeyTheme
import com.artembolotov.twinkey.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {

    // Тот же экземпляр, что получила бы WelcomeScreen() через viewModel() по умолчанию —
    // здесь он нужен явно, чтобы передать в него открытый извне .twinkey-файл.
    private val vm: AccountsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        handleFileIntent(intent)
        setContent {
            TwinKeyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WelcomeScreen(vm = vm)
                }
            }
        }
    }

    // launchMode="singleTask" (см. AndroidManifest) гарантирует, что повторное открытие
    // .twinkey-файла при уже запущенном приложении попадает сюда, а не создаёт новую
    // Activity — состояние и ViewModel переиспользуются, приложение просто выходит
    // на передний план.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFileIntent(intent)
    }

    // .twinkey-файл приходит двумя путями: ACTION_VIEW ("Открыть с помощью") кладёт Uri
    // в intent.data, ACTION_SEND ("Поделиться") — в EXTRA_STREAM.
    private fun handleFileIntent(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java
            )
            else -> null
        } ?: return
        vm.importBackupFile(uri)
    }
}
