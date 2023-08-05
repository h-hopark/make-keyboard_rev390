package ee.oyatl.ime.make

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import ee.oyatl.ime.make.keyboard.KeyEvent
import ee.oyatl.ime.make.keyboard.Keyboard
import ee.oyatl.ime.make.keyboard.KeyboardConfig
import ee.oyatl.ime.make.keyboard.commandOutput
import ee.oyatl.ime.make.modifier.DefaultShiftKeyHandler
import ee.oyatl.ime.make.modifier.ModifierKeyHandler
import ee.oyatl.ime.make.modifier.ModifierKeyState

class IMEService: InputMethodService() {
    private val handler: Handler = Handler(Looper.getMainLooper())

    private var inputView: View? = null
    private val inputViewLifecycleOwner = InputViewLifecycleOwner()

    private val shiftHandler: ModifierKeyHandler = DefaultShiftKeyHandler(500)
    private val initialKeyboardConfig = KeyboardConfigs.generate(ModifierKeyState())

    override fun onCreate() {
        super.onCreate()
        inputViewLifecycleOwner.onCreate()
    }

    override fun onCreateInputView(): View {
        inputViewLifecycleOwner.attachToDecorView(window?.window?.decorView)
        return ComposeView(this).apply {
            setContent {
                InputView(initialKeyboardConfig)
            }
            this@IMEService.inputView = this
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        inputViewLifecycleOwner.onResume()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        inputViewLifecycleOwner.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        inputViewLifecycleOwner.onDestroy()
    }

    private fun onKeyEvent(event: KeyEvent) {
        val inputConnection = currentInputConnection ?: return
        val commandOutput = event.output.commandOutput
        when(event.action) {
            KeyEvent.Action.Press -> {
                if(commandOutput != null) {
                    onCommandPress(commandOutput)
                } else {
                    val output = if(shiftHandler.state.active) event.output.uppercase()
                    else event.output.lowercase()
                    inputConnection.commitText(output, 1)
                    shiftHandler.autoUnlock()
                    shiftHandler.onInput()
                }
                performKeyFeedback(event.output)
            }
            KeyEvent.Action.Release -> {
                if(commandOutput != null) {
                    onCommandRelease(commandOutput)
                }
            }
            KeyEvent.Action.Repeat -> {
                if(commandOutput != null) {
                    onCommandRepeat(commandOutput)
                }
            }
        }
    }

    private fun onCommandPress(actionId: String) {
        val inputConnection = currentInputConnection ?: return
        when(actionId) {
            "DELETE" -> {
                inputConnection.deleteSurroundingText(1, 0)
                fun repeat() {
                    this.onKeyEvent(KeyEvent(KeyEvent.Action.Repeat, "<<DELETE>>"))
                    handler.postDelayed({ repeat() }, 50)
                }
                handler.postDelayed({ repeat() }, 500)
            }
            "SHIFT" -> {
                shiftHandler.onPress()
            }
            "RETURN" -> {
                sendDefaultEditorAction(true)
            }
        }
    }

    private fun onCommandRelease(actionId: String) {
        val inputConnection = currentInputConnection ?: return
        when(actionId) {
            "DELETE" -> {
                handler.removeCallbacksAndMessages(null)
            }
            "SHIFT" -> {
                shiftHandler.onRelease()
            }
        }
    }

    private fun onCommandRepeat(actionId: String) {
        val inputConnection = currentInputConnection ?: return
        when(actionId) {
            "DELETE" -> {
                inputConnection.deleteSurroundingText(1, 0)
            }
        }
    }

    @Composable
    private fun InputView(keyboardConfig: KeyboardConfig) {
        val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val darkTheme = isSystemInDarkTheme()
        val colorScheme = when {
            dynamicColor && darkTheme -> dynamicDarkColorScheme(this)
            dynamicColor && !darkTheme -> dynamicLightColorScheme(this)
            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }
        val shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(24.dp),
        )

        var shiftState by remember { mutableStateOf(ModifierKeyState()) }
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
        ) {
            Keyboard(
                config = keyboardConfig.mapTextLabels {
                    if(shiftState.active) it.uppercase()
                    else it.lowercase()
                },
                onKeyEvent = {
                    this.onKeyEvent(it)
                    shiftState = this.shiftHandler.state
                },
            )
        }
    }

    private fun performKeyFeedback(output: String) {
        if(output.isNotEmpty()) {
            performHapticFeedback(output)
            performSoundFeedback(output)
        }
    }

    private fun performHapticFeedback(output: String) {
        val inputView = inputView ?: return
        inputView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun performSoundFeedback(output: String) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val fx = when(output.commandOutput) {
            "DELETE" -> AudioManager.FX_KEYPRESS_DELETE
            "RETURN" -> AudioManager.FX_KEYPRESS_RETURN
            "SPACE" -> AudioManager.FX_KEYPRESS_SPACEBAR
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audioManager.playSoundEffect(fx, 1f)
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        outInsets?.contentTopInsets = outInsets?.visibleTopInsets
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        super.onEvaluateFullscreenMode()
        return false
    }
}