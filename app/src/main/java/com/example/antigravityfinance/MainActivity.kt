package com.example.antigravityfinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.antigravityfinance.service.notification.TransactionNotificationListenerService
import com.example.antigravityfinance.theme.AntigravityFinanceTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Android unbinds the notification listener whenever it wants to; without asking for a
    // rebind on every launch the app silently stops reading transaction notifications.
    TransactionNotificationListenerService.requestRebind(applicationContext)

    enableEdgeToEdge()
    setContent {
      AntigravityFinanceTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
