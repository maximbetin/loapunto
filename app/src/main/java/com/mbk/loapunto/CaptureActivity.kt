package com.mbk.loapunto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mbk.loapunto.ui.CaptureSheet
import com.mbk.loapunto.ui.LoApuntoTheme

/** Opened by the FAB, the launcher shortcut, the Quick Settings tile and the share sheet. */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        setContent {
            LoApuntoTheme {
                CaptureSheet(initial = shared, onSave = EntryStore::add, onClose = ::finish)
            }
        }
    }
}
