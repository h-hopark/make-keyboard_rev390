package ee.oyatl.ime.make.module.component

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.view.View
import androidx.preference.PreferenceManager
import ee.oyatl.ime.make.state.ModifierKeyState
import ee.oyatl.ime.make.state.KeyboardState
import ee.oyatl.ime.make.module.candidates.Candidate
import ee.oyatl.ime.make.module.candidates.CandidateListener
import ee.oyatl.ime.make.module.inputengine.InputEngine
import ee.oyatl.ime.make.module.keyboardview.CanvasKeyboardView
import ee.oyatl.ime.make.module.keyboardview.FlickDirection
import ee.oyatl.ime.make.module.keyboardview.FlickLongPressAction
import ee.oyatl.ime.make.module.keyboardview.KeyboardListener
import ee.oyatl.ime.make.module.keyboardview.KeyboardView
import ee.oyatl.ime.make.module.keyboardview.StackedViewKeyboardView
import ee.oyatl.ime.make.module.keyboardview.Themes
import ee.oyatl.ime.make.preset.softkeyboard.Key
import ee.oyatl.ime.make.preset.softkeyboard.Keyboard
import ee.oyatl.ime.make.state.DefaultShiftKeyHandler

class KeyboardComponent(
    val keyboard: Keyboard,
    val rowHeight: Int,
    val direct: Boolean = false,
    autoUnlockShift: Boolean = true,
    private val disableTouch: Boolean = false,
): InputViewComponent, KeyboardListener, CandidateListener {

    var connectedInputEngine: InputEngine? = null
    private var shiftKeyHandler: DefaultShiftKeyHandler = DefaultShiftKeyHandler(autoUnlock = autoUnlockShift)

    private var keyboardViewType: String = "canvas"

    private var longPressAction: FlickLongPressAction = FlickLongPressAction.Shifted
    private var flickUpAction: FlickLongPressAction = FlickLongPressAction.Shifted
    private var flickDownAction: FlickLongPressAction = FlickLongPressAction.Symbols
    private var flickLeftAction: FlickLongPressAction = FlickLongPressAction.None
    private var flickRightAction: FlickLongPressAction = FlickLongPressAction.None

    private var keyboardView: KeyboardView? = null

    private var _state: KeyboardState = KeyboardState()
    private val state: KeyboardState get() = _state.copy(shift = shiftKeyHandler.state)
    private var ignoreCode: Int = 0

    override fun initView(context: Context): View? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        shiftKeyHandler.reset()
        shiftKeyHandler.doubleTapGap = preferences.getFloat("behaviour_double_tap_gap", 500f).toInt()
        keyboardViewType = preferences.getString("appearance_keyboard_view_type", "canvas") ?: keyboardViewType
        longPressAction = FlickLongPressAction.of(
            preferences.getString("behaviour_long_press_action", "shift") ?: "shift"
        )
        flickUpAction = FlickLongPressAction.of(
            preferences.getString("behaviour_flick_action_up", "shift") ?: "shift"
        )
        flickDownAction = FlickLongPressAction.of(
            preferences.getString("behaviour_flick_action_down", "symbol") ?: "symbol"
        )
        flickLeftAction = FlickLongPressAction.of(
            preferences.getString("behaviour_flick_action_left", "none") ?: "none"
        )
        flickRightAction = FlickLongPressAction.of(
            preferences.getString("behaviour_flick_action_", "none") ?: "none"
        )

        val name = preferences.getString("appearance_theme", "theme_dynamic")
        val theme = Themes.ofName(name)
        keyboardView = when(keyboardViewType) {
            "stacked_view" -> StackedViewKeyboardView(context, null, keyboard, theme, this, rowHeight, disableTouch)
            else -> CanvasKeyboardView(context, null, keyboard, theme, this, rowHeight, disableTouch = disableTouch)
        }
        return keyboardView
    }

    override fun reset() {
        updateView()
        val inputEngine = connectedInputEngine ?: return
        inputEngine.onReset()
    }

    override fun onItemClicked(candidate: Candidate) {
        val inputEngine = connectedInputEngine ?: return
        if(inputEngine is CandidateListener) inputEngine.onItemClicked(candidate)
    }

    override fun updateView() {
        val inputEngine = connectedInputEngine ?: return
        updateLabelsAndIcons(
            getShiftedLabels(state.shift) + inputEngine.getLabels(state),
            inputEngine.getIcons(state)
        )
        updateMoreKeys(inputEngine.getMoreKeys(state))
        keyboardView?.apply {
            invalidate()
        }
    }

    private fun getShiftedLabels(shiftState: ModifierKeyState): Map<Int, CharSequence> {
        fun label(label: String) =
            if(shiftState.pressed || shiftState.locked) label.uppercase()
            else label.lowercase()
        return keyboard.rows.flatMap { it.keys }
            .filterIsInstance<Key>()
            .associate { it.code to label(it.label.orEmpty()) }
    }

    private fun updateLabelsAndIcons(labels: Map<Int, CharSequence>, icons: Map<Int, Drawable>) {
        val keyboardView = keyboardView ?: return
        keyboardView.updateLabelsAndIcons(labels, icons)
    }

    private fun updateMoreKeys(moreKeys: Map<Int, Keyboard>) {
        val keyboardView = keyboardView ?: return
        keyboardView.updateMoreKeyKeyboards(moreKeys)
    }

    override fun onKeyDown(code: Int, output: String?) {
        when(code) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                shiftKeyHandler.onPress()
                updateView()
            }
        }
    }

    override fun onKeyUp(code: Int, output: String?) {
        when(code) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                shiftKeyHandler.onRelease()
                updateView()
            }
        }
    }

    override fun onKeyClick(code: Int, output: String?) {
        val inputEngine = connectedInputEngine ?: return
        if(ignoreCode != 0 && ignoreCode == code) {
            ignoreCode = 0
            return
        }
        when(code) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
            }
            KeyEvent.KEYCODE_CAPS_LOCK -> {
                shiftKeyHandler.onLock()
            }
            KeyEvent.KEYCODE_DEL -> {
                inputEngine.onDelete()
            }
            KeyEvent.KEYCODE_SPACE -> {
                val state = _state.copy()
                reset()
                this._state = state
                inputEngine.listener.onCommitText(" ")
                onInput()
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                reset()
                inputEngine.listener.onEditorAction(code)
                onInput()
            }
            else -> {
                if(!inputEngine.listener.onSystemKey(code)) {
                    onPrintingKey(code, output)
                }
            }
        }
        updateView()
    }

    override fun onKeyLongClick(code: Int, output: String?) {
        val inputEngine = connectedInputEngine ?: return
        longPressAction.onKey(code, state, inputEngine)
        ignoreCode = code
        onInput()
    }

    private fun onPrintingKey(code: Int, output: String?) {
        val inputEngine = connectedInputEngine ?: return
        if(code == 0 && output != null) {
            inputEngine.listener.onCommitText(output)
        } else {
            inputEngine.onKey(code, state)
        }
        onInput()
    }

    override fun onKeyFlick(direction: FlickDirection, code: Int, output: String?) {
        val inputEngine = connectedInputEngine ?: return
        val action = when(direction) {
            FlickDirection.Up -> flickUpAction
            FlickDirection.Down -> flickDownAction
            FlickDirection.Left -> flickLeftAction
            FlickDirection.Right -> flickRightAction
            else -> FlickLongPressAction.None
        }
        action.onKey(code, state, inputEngine)
        ignoreCode = code
        onInput()
    }

    private fun onInput() {
        shiftKeyHandler.onInput()
    }
}