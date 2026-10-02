package com.mbk.loapunto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mbk.loapunto.ui.LoApuntoApp
import com.mbk.loapunto.ui.LoApuntoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LoApuntoTheme {
                LoApuntoApp(onCapture = { startActivity(Intent(this, CaptureActivity::class.java)) })
            }
        }
    }
}
