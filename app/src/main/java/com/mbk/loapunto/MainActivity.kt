package com.mbk.loapunto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mbk.loapunto.ui.LoApuntoApp
import com.mbk.loapunto.ui.LoApuntoTheme

class MainActivity : ComponentActivity() {
    /** Entry to open, set when a reminder notification is tapped. */
    private var openRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest = intent.getStringExtra(Reminders.EXTRA_ENTRY_ID)
        setContent {
            LoApuntoTheme {
                LoApuntoApp(
                    openRequest = openRequest,
                    onOpenHandled = { openRequest = null },
                    onCapture = { startActivity(Intent(this, CaptureActivity::class.java)) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Reminders.EXTRA_ENTRY_ID)?.let { openRequest = it }
    }
}
