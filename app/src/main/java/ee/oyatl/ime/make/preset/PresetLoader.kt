package ee.oyatl.ime.make.preset

import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import androidx.preference.PreferenceManager
import kotlin.math.roundToInt

class PresetLoader(
    val context: Context,
) {
    private val pref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val screenMode: String = pref.getString("layout_screen_mode", "mobile") ?: "mobile"

    private val unifyHeight: Boolean = pref.getBoolean("appearance_unify_height", false)
    private val rowHeight: Int = pref.getFloat("appearance_keyboard_height", 55f).roundToInt()

    fun mod(preset: InputEnginePreset): InputEnginePreset {
        return preset.copy(
            layout = modLayout(preset.layout),
            size = InputEnginePreset.Size(rowHeight = modHeight(preset.size.rowHeight)),
        )
    }

    private fun modHeight(height: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            height.toFloat(),
            context.resources.displayMetrics
        ).roundToInt()
    }

    private fun modSize(size: InputEnginePreset.Size): InputEnginePreset.Size {
        val rowHeight: Int = modHeight(if(size.defaultHeight) rowHeight else size.rowHeight)
        return size.copy(
            unifyHeight = unifyHeight,
            rowHeight = rowHeight
        )
    }

    fun modFilenames(fileNames: List<String>): List<String> {
        val screenMode = when(screenMode) {
            "television" -> "mobile"
            else -> screenMode
        }
        return fileNames.map { it.format(screenMode) }
    }

    private fun modLayout(layout: InputEnginePreset.Layout): InputEnginePreset.Layout {
        val moreKeysTable = mutableListOf<String>()
        val overrideTable = mutableListOf<String>()
        moreKeysTable += "symbol/morekeys_common.yaml"
        return InputEnginePreset.Layout(
            softKeyboard = modFilenames(layout.softKeyboard),
            moreKeysTable = modFilenames(layout.moreKeysTable) + moreKeysTable,
            codeConvertTable = modFilenames(layout.codeConvertTable),
            overrideTable = modFilenames(layout.overrideTable) + overrideTable,
            combinationTable = modFilenames(layout.combinationTable),
        )
    }

    private fun modPreset(preset: InputEnginePreset): InputEnginePreset {
        return preset.copy(
            layout = modLayout(preset.layout),
            size = modSize(preset.size),
        )
    }

    fun modLatin(preset: InputEnginePreset): InputEnginePreset = modPreset(preset)
    fun modHangul(preset: InputEnginePreset): InputEnginePreset = modPreset(preset)
    fun modSymbol(preset: InputEnginePreset, language: String): InputEnginePreset {
        val moreKeysTable = mutableListOf<String>()
        val overrideTable = mutableListOf<String>()
        when(language) {
            "ko" -> {
                moreKeysTable += "symbol/morekeys_symbols_hangul.yaml"
                overrideTable += "symbol/override_currency_won.yaml"
            }
        }
        return preset.copy(
            layout = preset.layout.copy(
                softKeyboard = modFilenames(preset.layout.softKeyboard),
                moreKeysTable = modFilenames(preset.layout.moreKeysTable) + moreKeysTable,
                codeConvertTable = modFilenames(preset.layout.codeConvertTable),
                overrideTable = modFilenames(preset.layout.overrideTable) + overrideTable,
                combinationTable = modFilenames(preset.layout.combinationTable),
            ),
            size = modSize(preset.size),
        )
    }

}