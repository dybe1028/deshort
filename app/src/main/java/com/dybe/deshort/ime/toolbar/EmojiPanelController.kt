package com.dybe.deshort.ime.toolbar

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.dybe.deshort.R
import com.dybe.deshort.data.DeshortPreferences

/**
 * EmojiPanelController — manages the emoji picker panel.
 *
 * Features:
 *  - 8 categories including Recent (populated from prefs)
 *  - Category tab strip (scrollable)
 *  - Auto-fit GridView with all emoji
 *  - Tap emoji → commit to input, save to recent
 */
class EmojiPanelController(
    private val context: Context,
    private val prefs: DeshortPreferences,
    private val onEmojiSelected: (String) -> Unit,
    private val onPanelVisibilityChanged: (Boolean) -> Unit
) {

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------

    private var emojiPanel: View? = null
    private var categoryTabsContainer: LinearLayout? = null
    private var emojiGrid: GridView? = null
    private var currentCategoryIndex: Int = 1 // Default: Smileys

    // -------------------------------------------------------------------------
    // Adapter
    // -------------------------------------------------------------------------

    private inner class EmojiAdapter(private val emojis: List<String>) : BaseAdapter() {
        override fun getCount(): Int = emojis.size
        override fun getItem(position: Int): Any = emojis[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val dp = context.resources.displayMetrics.density
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams((44 * dp).toInt(), (44 * dp).toInt())
                gravity = Gravity.CENTER
                textSize = 22f
                typeface = Typeface.DEFAULT
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.bg_toolbar_btn)
            }
            tv.text = emojis[position]
            tv.setOnClickListener {
                val emoji = emojis[position]
                onEmojiSelected(emoji)
                saveToRecent(emoji)
            }
            return tv
        }
    }

    // -------------------------------------------------------------------------
    // Bind
    // -------------------------------------------------------------------------

    fun bindViews(rootView: View) {
        emojiPanel = rootView.findViewById(R.id.emoji_panel_container)
        categoryTabsContainer = rootView.findViewById(R.id.emoji_category_tabs)
        emojiGrid = rootView.findViewById(R.id.emoji_grid)

        rootView.findViewById<Button>(R.id.emoji_back_btn)?.setOnClickListener {
            hidePanel()
        }

        buildCategoryTabs()
    }

    // -------------------------------------------------------------------------
    // Panel visibility
    // -------------------------------------------------------------------------

    fun showPanel() {
        emojiPanel?.visibility = View.VISIBLE
        onPanelVisibilityChanged(true)
        loadCategory(currentCategoryIndex)
    }

    fun hidePanel() {
        emojiPanel?.visibility = View.GONE
        onPanelVisibilityChanged(false)
    }

    fun isPanelVisible(): Boolean = emojiPanel?.visibility == View.VISIBLE

    // -------------------------------------------------------------------------
    // Category tabs
    // -------------------------------------------------------------------------

    private fun buildCategoryTabs() {
        val container = categoryTabsContainer ?: return
        container.removeAllViews()
        val dp = context.resources.displayMetrics.density

        val allCategories = buildDisplayCategories()
        allCategories.forEachIndexed { index, category ->
            val tab = TextView(context).apply {
                text = category.icon
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    (40 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT
                ).also { it.marginEnd = (2 * dp).toInt() }
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                setBackgroundResource(R.drawable.bg_toolbar_btn)
                isClickable = true
                isFocusable = true
                setOnClickListener { selectCategory(index) }
            }
            container.addView(tab)
        }
    }

    private fun selectCategory(index: Int) {
        currentCategoryIndex = index
        // Update tab backgrounds
        val container = categoryTabsContainer ?: return
        for (i in 0 until container.childCount) {
            val tab = container.getChildAt(i)
            tab.setBackgroundResource(
                if (i == index) R.drawable.bg_toolbar_btn_active else R.drawable.bg_toolbar_btn
            )
        }
        loadCategory(index)
    }

    private fun loadCategory(index: Int) {
        val categories = buildDisplayCategories()
        val category = categories.getOrNull(index) ?: return
        emojiGrid?.adapter = EmojiAdapter(category.emojis)
    }

    // -------------------------------------------------------------------------
    // Recent emoji
    // -------------------------------------------------------------------------

    private fun saveToRecent(emoji: String) {
        val recent = getRecentEmojis().toMutableList()
        recent.remove(emoji)
        recent.add(0, emoji)
        val trimmed = recent.take(24)
        // Store using a simple convention through the existing prefs SharedPreferences
        val sp = context.getSharedPreferences(DeshortPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(PREF_RECENT_EMOJI, trimmed.joinToString("|")).apply()
    }

    private fun getRecentEmojis(): List<String> {
        val sp = context.getSharedPreferences(DeshortPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(PREF_RECENT_EMOJI, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("|").filter { it.isNotEmpty() }
    }

    private fun buildDisplayCategories(): List<EmojiData.Category> {
        val recent = EmojiData.categories[0].copy(emojis = getRecentEmojis())
        return listOf(recent) + EmojiData.displayCategories
    }

    companion object {
        private const val PREF_RECENT_EMOJI = "pref_recent_emoji"
    }
}
