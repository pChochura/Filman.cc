package com.pointlessapps.filman.ui.core

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.InputConnectionWrapper
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Intercepts platform text input to support voice dictation on Android TV (e.g. from Google Keyboard / Gboard).
 *
 * Android TV Google Keyboard delivers voice dictation results via `commitCompletion` and related
 * InputConnection callbacks, which are unsupported and dropped by Compose's default input connection.
 * This wrapper intercepts those calls, updates [textFieldState], and triggers [onSubmit].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InterceptVoiceDictation(
    textFieldState: TextFieldState,
    onSubmit: ((String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val currentOnSubmit = rememberUpdatedState(onSubmit)

    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val mainHandler = Handler(Looper.getMainLooper())
            val wrappedRequest =
                PlatformTextInputMethodRequest { outAttributes ->
                    val baseConnection = request.createInputConnection(outAttributes)
                    object : InputConnectionWrapper(baseConnection, false) {
                        private fun updateTextAndSubmit(newText: String) {
                            if (newText.isEmpty()) return
                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                textFieldState.setTextAndPlaceCursorAtEnd(newText)
                                currentOnSubmit.value?.invoke(newText)
                            } else {
                                mainHandler.post {
                                    textFieldState.setTextAndPlaceCursorAtEnd(newText)
                                    currentOnSubmit.value?.invoke(newText)
                                }
                            }
                        }

                        override fun commitCompletion(text: CompletionInfo?): Boolean {
                            val spokenText = text?.text?.toString()
                            if (!spokenText.isNullOrEmpty()) {
                                updateTextAndSubmit(spokenText)
                                return true
                            }
                            return super.commitCompletion(text)
                        }

                        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean {
                            val correctedText = correctionInfo?.newText?.toString()
                            if (!correctedText.isNullOrEmpty()) {
                                updateTextAndSubmit(correctedText)
                                return true
                            }
                            return super.commitCorrection(correctionInfo)
                        }

                        override fun performPrivateCommand(
                            action: String?,
                            data: Bundle?,
                        ): Boolean {
                            val voiceResult =
                                data?.getStringArrayList("results_recognition")?.firstOrNull()
                                    ?: data?.getString("query")
                                    ?: data?.getCharSequence("text")?.toString()
                            if (!voiceResult.isNullOrEmpty()) {
                                updateTextAndSubmit(voiceResult)
                                return true
                            }
                            return super.performPrivateCommand(action, data)
                        }

                        override fun sendKeyEvent(event: KeyEvent?): Boolean {
                            if (event?.action == KeyEvent.ACTION_MULTIPLE && event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                                val characters = event.characters
                                if (!characters.isNullOrEmpty()) {
                                    updateTextAndSubmit(characters)
                                    return true
                                }
                            } else if (event?.action == KeyEvent.ACTION_UP && 
                                (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_SEARCH || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                
                                val extracted = baseConnection.getExtractedText(
                                    android.view.inputmethod.ExtractedTextRequest(), 0
                                )?.text?.toString()
                                
                                if (extracted != null && extracted.isNotBlank() && extracted != textFieldState.text.toString()) {
                                    textFieldState.setTextAndPlaceCursorAtEnd(extracted)
                                }

                                val currentText = textFieldState.text.toString()
                                if (currentText.isNotBlank()) {
                                    if (Looper.myLooper() == Looper.getMainLooper()) {
                                        currentOnSubmit.value?.invoke(currentText)
                                    } else {
                                        mainHandler.post { currentOnSubmit.value?.invoke(currentText) }
                                    }
                                }
                            }
                            return super.sendKeyEvent(event)
                        }

                        override fun performEditorAction(editorAction: Int): Boolean {
                            val extracted = baseConnection.getExtractedText(
                                android.view.inputmethod.ExtractedTextRequest(), 0
                            )?.text?.toString()
                            
                            if (extracted != null && extracted.isNotBlank() && extracted != textFieldState.text.toString()) {
                                textFieldState.setTextAndPlaceCursorAtEnd(extracted)
                            }

                            val currentText = textFieldState.text.toString()
                            if (currentText.isNotBlank()) {
                                if (Looper.myLooper() == Looper.getMainLooper()) {
                                    currentOnSubmit.value?.invoke(currentText)
                                } else {
                                    mainHandler.post { currentOnSubmit.value?.invoke(currentText) }
                                }
                            }
                            return super.performEditorAction(editorAction)
                        }
                    }
                }
            nextHandler.startInputMethod(wrappedRequest)
        },
        content = content,
    )
}
