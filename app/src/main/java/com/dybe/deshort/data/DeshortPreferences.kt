package com.dybe.deshort.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Manages user preferences for Deshort Keyboard.
 * Uses SharedPreferences for all storage — no DataStore dependency needed.
 */
class DeshortPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -------------------------------------------------------------------------
    // Input Behaviour
    // -------------------------------------------------------------------------

    /** Tự động viết hoa chữ cái ở đầu câu (., ?, !) — Default: true */
    var isAutoCapitalizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAPITALIZATION, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAPITALIZATION, value).apply()

    /** Tự động quay về bảng chữ khi bấm Cách sau ký tự đặc biệt — Default: true */
    var isAutoSwitchBackAbcEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SWITCH_BACK_ABC, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SWITCH_BACK_ABC, value).apply()

    // -------------------------------------------------------------------------
    // Appearance / Theme
    // -------------------------------------------------------------------------

    /**
     * Theme mode:
     *   THEME_LIGHT = "light"
     *   THEME_DARK  = "dark"
     *   THEME_SYSTEM = "system"
     *   THEME_SCHEDULED = "scheduled"
     */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_DARK) ?: THEME_DARK
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    /**
     * Start hour for Light mode in Scheduled theme (24-h, local time). Default 6.
     */
    var scheduledLightStartHour: Int
        get() = prefs.getInt(KEY_SCHEDULED_LIGHT_HOUR, 6)
        set(value) = prefs.edit().putInt(KEY_SCHEDULED_LIGHT_HOUR, value).apply()

    /**
     * Start hour for Dark mode in Scheduled theme (24-h, local time). Default 18.
     */
    var scheduledDarkStartHour: Int
        get() = prefs.getInt(KEY_SCHEDULED_DARK_HOUR, 18)
        set(value) = prefs.edit().putInt(KEY_SCHEDULED_DARK_HOUR, value).apply()

    /** Toolbar visibility — Default: true */
    var isToolbarVisible: Boolean
        get() = prefs.getBoolean(KEY_TOOLBAR_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_TOOLBAR_VISIBLE, value).apply()

    // -------------------------------------------------------------------------
    // Clipboard
    // -------------------------------------------------------------------------

    /** Enable clipboard shortcut pill preview — Default: true */
    var isClipboardPreviewEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD_PREVIEW, true)
        set(value) = prefs.edit().putBoolean(KEY_CLIPBOARD_PREVIEW, value).apply()

    /**
     * Clipboard history as JSON array of strings.
     * Max [MAX_CLIPBOARD_ITEMS] items. Pinned items are stored separately.
     */
    fun getClipboardHistory(): List<String> {
        val json = prefs.getString(KEY_CLIPBOARD_HISTORY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveClipboardHistory(items: List<String>) {
        val arr = JSONArray()
        items.take(MAX_CLIPBOARD_ITEMS).forEach { arr.put(it) }
        prefs.edit().putString(KEY_CLIPBOARD_HISTORY, arr.toString()).apply()
    }

    fun addClipboardItem(text: String) {
        if (text.isBlank()) return
        val existing = getClipboardHistory().toMutableList()
        existing.remove(text) // Remove duplicate if exists
        existing.add(0, text) // Add to front
        saveClipboardHistory(existing.take(MAX_CLIPBOARD_ITEMS))
    }

    fun removeClipboardItem(text: String) {
        val existing = getClipboardHistory().toMutableList()
        existing.remove(text)
        saveClipboardHistory(existing)
    }

    fun clearClipboardHistory() {
        prefs.edit()
            .putString(KEY_CLIPBOARD_HISTORY, "[]")
            .apply()
    }

    /** Pinned clipboard items (never auto-deleted) */
    fun getPinnedItems(): List<String> {
        val json = prefs.getString(KEY_CLIPBOARD_PINNED, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun pinItem(text: String) {
        if (text.isBlank()) return
        val existing = getPinnedItems().toMutableList()
        if (!existing.contains(text)) {
            existing.add(0, text)
            val arr = JSONArray()
            existing.take(MAX_PINNED_ITEMS).forEach { arr.put(it) }
            prefs.edit().putString(KEY_CLIPBOARD_PINNED, arr.toString()).apply()
        }
    }

    fun unpinItem(text: String) {
        val existing = getPinnedItems().toMutableList()
        existing.remove(text)
        val arr = JSONArray()
        existing.forEach { arr.put(it) }
        prefs.edit().putString(KEY_CLIPBOARD_PINNED, arr.toString()).apply()
    }

    // -------------------------------------------------------------------------
    // Keyboard Feel
    // -------------------------------------------------------------------------

    /** Key vibration on press — Default: false */
    var isKeyVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, false)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    /** Key sound on press — Default: false */
    var isKeySoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    /** Long press delay in ms for backspace repeat — Default: 400 */
    var longPressDelayMs: Int
        get() = prefs.getInt(KEY_LONG_PRESS_DELAY, 400)
        set(value) = prefs.edit().putInt(KEY_LONG_PRESS_DELAY, value).apply()

    // -------------------------------------------------------------------------
    // Listener helpers
    // -------------------------------------------------------------------------

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        const val PREFS_NAME = "deshort_keyboard_preferences"

        // Input behaviour
        const val KEY_AUTO_CAPITALIZATION = "pref_auto_capitalization"
        const val KEY_AUTO_SWITCH_BACK_ABC = "pref_auto_switch_back_abc"

        // Theme
        const val KEY_THEME_MODE = "pref_theme_mode"
        const val KEY_SCHEDULED_LIGHT_HOUR = "pref_scheduled_light_hour"
        const val KEY_SCHEDULED_DARK_HOUR = "pref_scheduled_dark_hour"
        const val KEY_TOOLBAR_VISIBLE = "pref_toolbar_visible"

        // Theme mode values
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"
        const val THEME_SCHEDULED = "scheduled"

        // Clipboard
        const val KEY_CLIPBOARD_PREVIEW = "pref_clipboard_preview"
        const val KEY_CLIPBOARD_HISTORY = "pref_clipboard_history"
        const val KEY_CLIPBOARD_PINNED = "pref_clipboard_pinned"
        const val MAX_CLIPBOARD_ITEMS = 20
        const val MAX_PINNED_ITEMS = 10

        // Keyboard feel
        const val KEY_VIBRATION = "pref_key_vibration"
        const val KEY_SOUND = "pref_key_sound"
        const val KEY_LONG_PRESS_DELAY = "pref_long_press_delay"
    }
}
