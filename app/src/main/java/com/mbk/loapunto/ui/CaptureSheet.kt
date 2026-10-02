package com.mbk.loapunto.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mbk.loapunto.R

/**
 * Bottom card over a dim scrim. Tapping outside or going back keeps what you typed;
 * only Cancel throws it away.
 */
@Composable
fun CaptureSheet(initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var savedCount by rememberSaveable { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val speech = remember { speechIntent(context) }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { heard ->
            text = listOf(text.trimEnd(), heard.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        }
        focus.requestFocus()
    }

    fun saveCurrent() {
        if (text.isNotBlank()) onSave(text.trim())
        text = ""
    }
    fun keepAndClose() {
        saveCurrent()
        onClose()
    }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    BackHandler(onBack = ::keepAndClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = ::keepAndClose)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                // Swallow taps so they don't reach the scrim.
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.navigationBarsPadding().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.new_entry),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    if (savedCount > 0) {
                        Text(
                            pluralStringResource(R.plurals.n_saved, savedCount, savedCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (speech != null) {
                        IconButton(onClick = { listen.launch(speech) }) {
                            Icon(
                                painterResource(R.drawable.ic_mic),
                                contentDescription = stringResource(R.string.dictate),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.capture_placeholder)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    keyboardOptions = TextKeyboard,
                    colors = transparentFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
                        .focusRequester(focus),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            saveCurrent()
                            savedCount++
                        },
                    ) { Text(stringResource(R.string.add_another)) }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = text.isNotBlank(), onClick = ::keepAndClose) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

private const val FUTO_VOICE = "org.futo.voiceinput"

/**
 * Speech goes to FUTO Voice Input (offline Whisper) when it's installed, otherwise to whatever
 * speech app the phone has. Null when there's none, and the mic button is hidden.
 */
private fun speechIntent(context: Context): Intent? {
    val generic = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    val futo = Intent(generic).setPackage(FUTO_VOICE)
    return listOf(futo, generic).firstOrNull { context.packageManager.queryIntentActivities(it, 0).isNotEmpty() }
}

@Composable
fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

/** Autocorrect off: it mangles mixed English/Spanish. Some keyboards may ignore the hint. */
val TextKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false)
