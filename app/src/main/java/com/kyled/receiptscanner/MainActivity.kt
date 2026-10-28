package com.kyled.receiptscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyled.receiptscanner.ui.AppNavigation
import com.kyled.receiptscanner.ui.screens.LoginScreen
import com.kyled.receiptscanner.ui.theme.ReceiptScannerTheme
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val auth = (application as ReceiptApp).container.authRepository

        setContent {
            ReceiptScannerTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    val session by auth.sessionStatus.collectAsStateWithLifecycle()
                    // Tablets, foldables and landscape phones get the list and a receipt side by side.
                    val twoPane = calculateWindowSizeClass(this).widthSizeClass == WindowWidthSizeClass.Expanded

                    when (session) {
                        // RefreshFailure: the token couldn't be refreshed (usually offline). Stay in the app.
                        is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> AppNavigation(twoPane = twoPane)
                        is SessionStatus.NotAuthenticated -> LoginScreen()
                        SessionStatus.Initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
