package com.dybe.deshort.ime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.dybe.deshort.R
import com.dybe.deshort.data.DeshortPreferences
import java.util.Calendar

/**
 * KeyboardThemeManager — applies light/dark colors to the IME views at runtime.
 *
 * Because keyboard_view.xml uses static @color references at inflation time, we
 * apply colors programmatically on every [applyTheme] call. This lets us switch
 * themes without reinflating the view.
 *
 * Theme selection order:
 *   1. Light / Dark override from prefs
 *   2. Follow System (night mode UI mode)
 *   3. Scheduled (compare current hour against prefs times, local timezone)
 *
 * No background services are used. Theme is applied in onStartInputView and
 * on demand when settings change.
 */
class KeyboardThemeManager(
    private val context: Context,
    private val prefs: DeshortPreferences
) {

    // -------------------------------------------------------------------------
    // Theme resolution
    // -------------------------------------------------------------------------

    /** Resolved to true = dark, false = light */
    fun isDarkTheme(): Boolean {
        return when (prefs.themeMode) {
            DeshortPreferences.THEME_LIGHT -> false
            DeshortPreferences.THEME_DARK -> true
            DeshortPreferences.THEME_SYSTEM -> isSystemDarkMode()
            DeshortPreferences.THEME_SCHEDULED -> isScheduledDarkTime()
            else -> true
        }
    }

    private fun isSystemDarkMode(): Boolean {
        val nightModeMask = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun isScheduledDarkTime(): Boolean {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val lightStart = prefs.scheduledLightStartHour  // e.g. 6
        val darkStart = prefs.scheduledDarkStartHour    // e.g. 18

        return if (lightStart < darkStart) {
            // Normal day: light from lightStart to darkStart, dark otherwise
            hour < lightStart || hour >= darkStart
        } else {
            // Edge case: e.g. lightStart=20, darkStart=8 → dark from 8..20
            hour in darkStart until lightStart
        }
    }

    // -------------------------------------------------------------------------
    // Theme Colors
    // -------------------------------------------------------------------------

    data class ThemeColors(
        val keyboardBg: Int,
        val keyLetterBg: Int,
        val keyLetterPressed: Int,
        val keySpecialBg: Int,
        val keySpecialPressed: Int,
        val keySpaceBg: Int,
        val keySpacePressed: Int,
        val keyText: Int,
        val keyTextSpecial: Int,
        val keyTextSpace: Int,
        val keyRipple: Int,
        val shiftActiveBg: Int,
        val shiftActivePressed: Int,
        val shiftActiveText: Int,
        val toolbarBg: Int,
        val toolbarBtnBg: Int,
        val toolbarBtnPressed: Int,
        val toolbarBtnActive: Int,
        val toolbarText: Int,
        val pillBg: Int,
        val pillText: Int
    )

    private fun darkColors() = ThemeColors(
        keyboardBg = Color.parseColor("#18191F"),
        keyLetterBg = Color.parseColor("#303137"),
        keyLetterPressed = Color.parseColor("#484A52"),
        keySpecialBg = Color.parseColor("#25262D"),
        keySpecialPressed = Color.parseColor("#383A42"),
        keySpaceBg = Color.parseColor("#303137"),
        keySpacePressed = Color.parseColor("#484A52"),
        keyText = Color.parseColor("#F4F4F5"),
        keyTextSpecial = Color.parseColor("#C0C2CC"),
        keyTextSpace = Color.parseColor("#7B7E8A"),
        keyRipple = Color.parseColor("#55FFFFFF"),
        shiftActiveBg = Color.parseColor("#9DB8FF"),
        shiftActivePressed = Color.parseColor("#7A9EFF"),
        shiftActiveText = Color.parseColor("#18191F"),
        toolbarBg = Color.parseColor("#202127"),
        toolbarBtnBg = Color.parseColor("#303137"),
        toolbarBtnPressed = Color.parseColor("#484A52"),
        toolbarBtnActive = Color.parseColor("#9DB8FF"),
        toolbarText = Color.parseColor("#B0B3C0"),
        pillBg = Color.parseColor("#303137"),
        pillText = Color.parseColor("#C0C2CC")
    )

    private fun lightColors() = ThemeColors(
        keyboardBg = Color.parseColor("#F0F1F4"),
        keyLetterBg = Color.parseColor("#FFFFFF"),
        keyLetterPressed = Color.parseColor("#D8DCE6"),
        keySpecialBg = Color.parseColor("#DDE1E8"),
        keySpecialPressed = Color.parseColor("#BFC4CF"),
        keySpaceBg = Color.parseColor("#FFFFFF"),
        keySpacePressed = Color.parseColor("#D8DCE6"),
        keyText = Color.parseColor("#202124"),
        keyTextSpecial = Color.parseColor("#3C4043"),
        keyTextSpace = Color.parseColor("#70757A"),
        keyRipple = Color.parseColor("#44000000"),
        shiftActiveBg = Color.parseColor("#526DA8"),
        shiftActivePressed = Color.parseColor("#3B5690"),
        shiftActiveText = Color.parseColor("#FFFFFF"),
        toolbarBg = Color.parseColor("#E5E7EC"),
        toolbarBtnBg = Color.parseColor("#FFFFFF"),
        toolbarBtnPressed = Color.parseColor("#D0D3DC"),
        toolbarBtnActive = Color.parseColor("#526DA8"),
        toolbarText = Color.parseColor("#5F6368"),
        pillBg = Color.parseColor("#FFFFFF"),
        pillText = Color.parseColor("#3C4043")
    )

    // -------------------------------------------------------------------------
    // Apply
    // -------------------------------------------------------------------------

    /**
     * Applies the resolved theme to the keyboard root view tree.
     * Call this after inflation or when theme settings change.
     */
    fun applyTheme(rootView: View) {
        val colors = if (isDarkTheme()) darkColors() else lightColors()
        applyColors(rootView, colors)
    }

    private fun applyColors(root: View, c: ThemeColors) {
        // Keyboard root background
        root.setBackgroundColor(c.keyboardBg)

        // Toolbar row
        root.findViewById<View>(R.id.toolbar_row)?.setBackgroundColor(c.toolbarBg)

        // Toolbar icon buttons
        listOf(
            R.id.toolbar_emoji,
            R.id.toolbar_clipboard_mgr,
            R.id.toolbar_cursor_left,
            R.id.toolbar_cursor_right,
            R.id.toolbar_text_select
        ).forEach { id ->
            root.findViewById<ImageButton>(id)?.apply {
                val normalBg = makeRoundRect(c.toolbarBtnBg, c.toolbarBtnPressed, c.keyRipple, 10)
                background = normalBg
                imageTintList = ColorStateList.valueOf(c.toolbarText)
            }
        }

        // Clipboard pill
        root.findViewById<View>(R.id.toolbar_clipboard_pill)?.apply {
            val pillBg = makeRoundRect(c.pillBg, c.toolbarBtnPressed, c.keyRipple, 20)
            background = pillBg
        }
        root.findViewById<TextView>(R.id.toolbar_pill_text)?.setTextColor(c.pillText)

        // QWERTY container
        root.findViewById<View>(R.id.qwerty_container)?.setBackgroundColor(c.keyboardBg)

        // All buttons
        applyButtonColors(root, c)

        // Panels backgrounds
        root.findViewById<View>(R.id.emoji_panel_container)?.setBackgroundColor(c.keyboardBg)
        root.findViewById<View>(R.id.clipboard_panel_container)?.setBackgroundColor(c.keyboardBg)
    }

    /**
     * Walk all Button children and apply color based on their tag.
     */
    private fun applyButtonColors(root: View, c: ThemeColors) {
        if (root is LinearLayout) {
            for (i in 0 until root.childCount) {
                applyButtonColors(root.getChildAt(i), c)
            }
        } else if (root is Button) {
            val tag = root.tag as? String ?: ""
            when {
                tag.contains("|") -> {
                    // Letter key
                    root.background = makeRoundRect(c.keyLetterBg, c.keyLetterPressed, c.keyRipple, 10)
                    root.setTextColor(c.keyText)
                }
                tag == "SPACE" -> {
                    root.background = makeRoundRect(c.keySpaceBg, c.keySpacePressed, c.keyRipple, 10)
                    root.setTextColor(c.keyTextSpace)
                }
                tag == "SHIFT" -> {
                    // Shift uses its own StateListDrawable (handled by bg_key_shift)
                    root.background = makeShiftDrawable(c)
                    root.setTextColor(c.keyTextSpecial)
                }
                tag == "ENTER" -> {
                    root.background = makeRoundRect(c.keySpecialBg, c.keySpecialPressed, c.keyRipple, 10)
                    root.setTextColor(c.shiftActiveBg) // accent color for enter
                }
                else -> {
                    // Special keys (MODE_SYM, BACKSPACE, SYM_TOGGLE etc)
                    root.background = makeRoundRect(c.keySpecialBg, c.keySpecialPressed, c.keyRipple, 10)
                    root.setTextColor(c.keyTextSpecial)
                }
            }
        }
    }

    /**
     * Update just the selection-mode toggle button appearance.
     */
    fun updateSelectionModeButton(rootView: View, isActive: Boolean) {
        val c = if (isDarkTheme()) darkColors() else lightColors()
        rootView.findViewById<ImageButton>(R.id.toolbar_text_select)?.apply {
            if (isActive) {
                background = makeRoundRect(c.toolbarBtnActive, c.shiftActivePressed, c.keyRipple, 10)
                imageTintList = ColorStateList.valueOf(c.shiftActiveText)
            } else {
                background = makeRoundRect(c.toolbarBtnBg, c.toolbarBtnPressed, c.keyRipple, 10)
                imageTintList = ColorStateList.valueOf(c.toolbarText)
            }
        }
    }

    /**
     * Update just the shift button appearance (active vs. normal).
     */
    fun updateShiftButton(shiftButton: Button, isActive: Boolean) {
        val c = if (isDarkTheme()) darkColors() else lightColors()
        shiftButton.background = if (isActive) {
            makeRoundRect(c.shiftActiveBg, c.shiftActivePressed, c.keyRipple, 10)
        } else {
            makeShiftDrawable(c)
        }
        shiftButton.setTextColor(if (isActive) c.shiftActiveText else c.keyTextSpecial)
    }

    // -------------------------------------------------------------------------
    // Drawable builders (no XML needed, avoids context-specific color references)
    // -------------------------------------------------------------------------

    private fun makeRoundRect(
        normal: Int,
        pressed: Int,
        ripple: Int,
        radiusDp: Int
    ): android.graphics.drawable.Drawable {
        val density = context.resources.displayMetrics.density
        val radius = radiusDp * density

        val normalShape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(normal)
        }
        val pressedShape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(pressed)
        }

        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedShape)
            addState(intArrayOf(), normalShape)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(ripple),
                stateList,
                normalShape
            )
        } else {
            stateList
        }
    }

    private fun makeShiftDrawable(c: ThemeColors): android.graphics.drawable.Drawable {
        // StateListDrawable: selected+pressed, selected, pressed, normal
        val density = context.resources.displayMetrics.density
        val radius = 10 * density

        fun shape(color: Int) = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }

        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected, android.R.attr.state_pressed), shape(c.shiftActivePressed))
            addState(intArrayOf(android.R.attr.state_selected), shape(c.shiftActiveBg))
            addState(intArrayOf(android.R.attr.state_pressed), shape(c.keySpecialPressed))
            addState(intArrayOf(), shape(c.keySpecialBg))
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(c.keyRipple),
                stateList,
                shape(c.keySpecialBg)
            )
        } else {
            stateList
        }
    }
}
