package ee.oyatl.ime.make

import android.inputmethodservice.InputMethodService
import android.os.Build
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

class IMEService: InputMethodService() {
    private val inputViewLifecycleOwner = InputViewLifecycleOwner()

    private var shiftPressed = false

    private val shiftKey = KeyConfig("<<SHIFT>>", "SFT", width = 1.5f)
    private val deleteKey = KeyConfig("<<DELETE>>", "DEL", width = 1.5f)
    private val initialKeyboardConfig: KeyboardConfig = KeyboardConfig(
        "QWERTYUIOP".toRowConfig(),
        "ASDFGHJKL".toRowConfig(0.5f, 0.5f),
        RowConfig(shiftKey) + "ZXCVBNM".toRowConfig(1.5f) + RowConfig(deleteKey),
    )

    override fun onCreate() {
        super.onCreate()
        inputViewLifecycleOwner.onCreate()
    }

    override fun onCreateInputView(): View {
        inputViewLifecycleOwner.attachToDecorView(window?.window?.decorView)
        return ComposeView(this).apply {
            setContent {
                InputView()
            }
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

    private fun onKeyClick(output: String) {
        val inputConnection = currentInputConnection ?: return
        when(output.uppercase()) {
            "<<DELETE>>" -> {
                inputConnection.deleteSurroundingText(1, 0)
            }
            "<<SHIFT>>" -> {
                shiftPressed = !shiftPressed
            }
            else -> {
//                val shiftedOutput = if(shiftState) output.uppercase() else output.lowercase()
//                inputConnection.commitText(shiftedOutput, 1)
                inputConnection.commitText(output, 1)
            }
        }
    }

    @Composable
    private fun InputView() {
        var keyboardConfig by remember { mutableStateOf(initialKeyboardConfig) }
        val onKeyClick: (String) -> Unit = {
            this.onKeyClick(it)
            keyboardConfig = initialKeyboardConfig.map { key ->
                val output = if(shiftPressed) key.output.uppercase() else key.output.lowercase()
                val keyTop = if(shiftPressed) key.keyTop.uppercase() else key.keyTop.lowercase()
                key.copy(output = output, keyTop = keyTop)
            }
        }
        onKeyClick("")
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
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
        ) {
            Keyboard(
                config = keyboardConfig,
                onKeyClick = { onKeyClick(it) },
            )
        }
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