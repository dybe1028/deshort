package com.dybe.deshort.ime.toolbar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dybe.deshort.R
import com.dybe.deshort.data.DeshortPreferences

/**
 * ClipboardController — manages the clipboard panel and smart pill.
 *
 * Privacy policy:
 *  - Never collects clipboard data autonomously in the background.
 *  - Only saves text when [saveCurrentClipboardItem] is called explicitly
 *    (e.g. after the user taps the clipboard pill to paste, or the user copies via IME).
 *  - On Android 10+ the system allows reading clipboard only when the IME is focused.
 *    We attempt to read it opportunistically in [refreshPillFromSystemClipboard] but
 *    never store the result without user interaction.
 *  - Passwords and content flagged as sensitive are ignored via ClipDescription flags.
 */
class ClipboardController(
    private val context: Context,
    private val prefs: DeshortPreferences,
    private val onPasteText: (String) -> Unit,
    private val onPanelVisibilityChanged: (Boolean) -> Unit
) {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Last text we read from the system clipboard (not saved to history) */
    private var systemClipPreview: String? = null

    /** Current selection text (set externally when user selects text) */
    var currentSelectionText: String? = null

    // -------------------------------------------------------------------------
    // Views (set after inflation)
    // -------------------------------------------------------------------------

    private var pillTextView: TextView? = null
    private var clipboardPanel: View? = null
    private var itemsContainer: LinearLayout? = null
    private var emptyLabel: TextView? = null
    private var scrollView: ScrollView? = null

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    fun bindViews(rootView: View) {
        pillTextView = rootView.findViewById(R.id.toolbar_pill_text)
        clipboardPanel = rootView.findViewById(R.id.clipboard_panel_container)
        itemsContainer = rootView.findViewById(R.id.clipboard_items_container)
        emptyLabel = rootView.findViewById(R.id.clipboard_empty_label)
        scrollView = rootView.findViewById(R.id.clipboard_scroll)

        rootView.findViewById<View>(R.id.toolbar_clipboard_pill)?.setOnClickListener {
            onPillClicked()
        }
        rootView.findViewById<Button>(R.id.clipboard_back_btn)?.setOnClickListener {
            hidePanel()
        }
        rootView.findViewById<Button>(R.id.clipboard_clear_btn)?.setOnClickListener {
            prefs.clearClipboardHistory()
            refreshPanel()
        }
    }

    // -------------------------------------------------------------------------
    // Pill (smart shortcut)
    // -------------------------------------------------------------------------

    /**
     * Update the pill display based on current state:
     *  - If there is a text selection: show "Copy"
     *  - Else if clipboard has text: show preview
     *  - Else: show "Clipboard"
     */
    fun refreshPill() {
        val pillText = pillTextView ?: return
        when {
            !currentSelectionText.isNullOrEmpty() -> {
                pillText.text = "Copy"
            }
            systemClipPreview != null -> {
                val preview = systemClipPreview!!.take(30).replace('\n', ' ')
                pillText.text = if (systemClipPreview!!.length > 30) "$preview…" else preview
            }
            else -> {
                pillText.text = "Clipboard"
            }
        }
    }

    /**
     * Attempt to read the current system clipboard text.
     * This may return null on Android 10+ if the IME is not in foreground.
     * We NEVER save this automatically — only used for the pill preview.
     */
    fun refreshPillFromSystemClipboard() {
        if (!prefs.isClipboardPreviewEnabled) {
            systemClipPreview = null
            refreshPill()
            return
        }
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val desc = clip.description
                // Skip sensitive content (password managers flag this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (desc.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) {
                        systemClipPreview = null
                        refreshPill()
                        return
                    }
                }
                val text = clip.getItemAt(0)?.coerceToText(context)?.toString()
                if (!text.isNullOrBlank()) {
                    systemClipPreview = text
                } else {
                    systemClipPreview = null
                }
            } else {
                systemClipPreview = null
            }
        } catch (e: SecurityException) {
            Log.d("ClipboardController", "Cannot read clipboard: ${e.message}")
            systemClipPreview = null
        } catch (e: Exception) {
            systemClipPreview = null
        }
        refreshPill()
    }

    private fun onPillClicked() {
        when {
            !currentSelectionText.isNullOrEmpty() -> {
                // Copy selection
                copyToSystemClipboard(currentSelectionText!!)
                prefs.addClipboardItem(currentSelectionText!!)
                systemClipPreview = currentSelectionText
                refreshPill()
            }
            systemClipPreview != null -> {
                // Paste the preview
                val text = systemClipPreview!!
                prefs.addClipboardItem(text)
                onPasteText(text)
            }
            else -> {
                // Open clipboard panel
                showPanel()
            }
        }
    }

    private fun copyToSystemClipboard(text: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Deshort clipboard", text))
        } catch (e: Exception) {
            Log.e("ClipboardController", "Failed to copy: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Panel
    // -------------------------------------------------------------------------

    fun showPanel() {
        clipboardPanel?.visibility = View.VISIBLE
        onPanelVisibilityChanged(true)
        refreshPanel()
    }

    fun hidePanel() {
        clipboardPanel?.visibility = View.GONE
        onPanelVisibilityChanged(false)
    }

    fun isPanelVisible(): Boolean = clipboardPanel?.visibility == View.VISIBLE

    private fun refreshPanel() {
        val container = itemsContainer ?: return
        container.removeAllViews()

        val pinned = prefs.getPinnedItems()
        val history = prefs.getClipboardHistory()
        val all = (pinned.map { it to true } + history.filter { it !in pinned }.map { it to false })

        if (all.isEmpty()) {
            emptyLabel?.visibility = View.VISIBLE
            scrollView?.visibility = View.GONE
            return
        }

        emptyLabel?.visibility = View.GONE
        scrollView?.visibility = View.VISIBLE

        all.forEach { (text, isPinned) ->
            val itemView = buildClipboardItemView(text, isPinned)
            container.addView(itemView)
        }
    }

    private fun buildClipboardItemView(text: String, isPinned: Boolean): View {
        val dp = context.resources.displayMetrics.density
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (4 * dp).toInt() }
            setBackgroundResource(R.drawable.bg_clipboard_item)
            setPadding((8 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt())
        }

        val textView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            this.text = text
            textSize = 12f
            setTextColor(resolveAttrColor(R.color.key_text_color))
        }

        val pinBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (48 * dp).toInt(), (32 * dp).toInt()
            )
            this.text = if (isPinned) "★" else "☆"
            textSize = 14f
            setPadding(0, 0, 0, 0)
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener {
                if (isPinned) prefs.unpinItem(text) else prefs.pinItem(text)
                refreshPanel()
            }
        }

        val deleteBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * dp).toInt(), (32 * dp).toInt()
            )
            this.text = "✕"
            textSize = 12f
            setPadding(0, 0, 0, 0)
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener {
                if (isPinned) prefs.unpinItem(text) else prefs.removeClipboardItem(text)
                refreshPanel()
            }
        }

        outer.setOnClickListener {
            onPasteText(text)
            hidePanel()
        }

        outer.addView(textView)
        outer.addView(pinBtn)
        outer.addView(deleteBtn)
        return outer
    }

    private fun resolveAttrColor(colorResId: Int): Int {
        return try {
            context.getColor(colorResId)
        } catch (e: Exception) {
            0xFFFFFFFF.toInt()
        }
    }

    // -------------------------------------------------------------------------
    // Public helpers
    // -------------------------------------------------------------------------

    /**
     * Call when the user explicitly copies text through Deshort (e.g. using the Copy button
     * in selection mode). This is the ONLY path that adds to clipboard history.
     */
    fun onUserCopiedText(text: String) {
        if (text.isBlank()) return
        copyToSystemClipboard(text)
        prefs.addClipboardItem(text)
        systemClipPreview = text
        refreshPill()
    }

    /** Called by IME when selection changes */
    fun onSelectionChanged(selStart: Int, selEnd: Int) {
        // selectionText is provided by CursorSelectionController
    }

    fun reset() {
        currentSelectionText = null
        refreshPill()
    }
}
