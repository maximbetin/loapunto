package com.mbk.loapunto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mbk.loapunto.ui.CaptureSheet
import com.mbk.loapunto.ui.LoApuntoTheme

/**
 * Opened by the FAB, the launcher shortcut, the Quick Settings tile and the share sheet.
 * The FAB passes the open tab in [EXTRA_LIST]; everything else lands in the inbox.
 */
class CaptureActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LIST = "list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val list = intent?.getStringExtra(EXTRA_LIST)
            ?.let { name -> Status.entries.firstOrNull { it.name == name } }
            ?.takeIf { it.isOrdered } ?: Status.INBOX
        val title = if (list == Status.INBOX) getString(R.string.new_entry) else getString(R.string.new_in, getString(list.label))
        setContent {
            LoApuntoTheme {
                CaptureSheet(initial = shared, title = title, onSave = { EntryStore.add(it, list) }, onClose = ::finish)
            }
        }
    }
}
