package ee.oyatl.ime.make.model

import android.content.Context
import android.util.TypedValue
import ee.oyatl.ime.make.profile.CommonKeyboardProfile
import ee.oyatl.ime.make.profile.BasicKeyboardProfile
import ee.oyatl.ime.make.table.CodeConvertTable
import ee.oyatl.ime.make.table.MoreKeysTable
import ee.oyatl.ime.make.view.keyboard.KeyboardListener
import ee.oyatl.ime.make.view.keyboard.StackedViewKeyboardView
import ee.oyatl.ime.make.view.keyboard.Themes

data class KeyboardProfilePreset(
    val type: Type,
    val keyboardLayout: KeyboardLayout,
    val convertTable: CodeConvertTable,
    val moreKeysTable: MoreKeysTable,
    val unifyHeight: Boolean = true,
    val rowHeight: Float = 55f,
    val doubleTapGap: Int = 500,
    val longPressDelay: Int = 500,
) {
    fun inflate(
        context: Context,
        keyboardListener: KeyboardListener,
        profileListener: CommonKeyboardProfile.Listener
    ): CommonKeyboardProfile {
        val rowHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, rowHeight, context.resources.displayMetrics).toInt()
        val keyboardView = StackedViewKeyboardView(
            context = context,
            attrs = null,
            listener = keyboardListener,
            keyboard = keyboardLayout,
            theme = Themes.Dynamic,
            popupOffsetY = 0,
            unifyHeight = unifyHeight,
            rowHeight = rowHeight
        )
        return if(type == Type.Alphabetic) BasicKeyboardProfile(keyboardView, convertTable, moreKeysTable, profileListener)
        else BasicKeyboardProfile.Symbols(keyboardView, convertTable, moreKeysTable, profileListener)
    }

    enum class Type {
        Alphabetic, Symbol,
    }
}
