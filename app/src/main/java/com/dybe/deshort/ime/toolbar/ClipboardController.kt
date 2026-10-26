package com.dybe.deshort.ime.toolbar

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
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
 *  - Only saves text when explicitly copied via IME or when pasted through smart pill.
 *  - On Android 10+ the system allows reading clipboard only when the IME is focused.
 *    We read opportunistically for the pill preview and never store text without user action.
 *  - Passwords and content flagged as IS_SENSITIVE are ignored.
 *  - Never persists sensitive clipboard text to disk just for tracking consumed state.
 */
class ClipboardController(
    private val context: Context,
    private val prefs: DeshortPreferences,
    private val onPasteText: (String) -> Boolean,
    private val onPasteImage: (Uri, ClipDescription) -> Boolean = { _, _ -> false },
    private val onSelectionDismissed: (() -> Unit)? = null,
    private val onPanelVisibilityChanged: (Boolean) -> Unit
) {

    // -------------------------------------------------------------------------
    // Transient Shortcut State
    // -------------------------------------------------------------------------

    /** Active text read from system clipboard (not yet consumed) */
    private var systemClipPreview: String? = null

    /** Active image URI read from system clipboard (not yet consumed) */
    private var systemClipImageUri: Uri? = null

    /** Active image description for rich content insertion */
    private var systemClipImageDescription: ClipDescription? = null

    /** Tiny thumbnail for image preview in pill (recycled when dismissed) */
    private var systemClipImageThumbnail: Bitmap? = null

    /** Timestamp of the currently displayed clip event */
    private var activeClipTimestamp: Long = 0L

    /** Hash of the currently displayed clip event */
    private var activeClipHash: Int = 0

    /** Sequence of the currently displayed clip event */
    private var activeClipSequence: Long = 0L

    /** Current selection text (set externally when user selects text) */
    var currentSelectionText: String? = null

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------

    private var pillTextView: TextView? = null
    private var pillImageView: ImageView? = null
    private var clipboardPanel: View? = null
    private var itemsContainer: LinearLayout? = null
    private var emptyLabel: TextView? = null
    private var scrollView: ScrollView? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        currentClipSequence++
        refreshPillFromSystemClipboard()
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    fun bindViews(rootView: View) {
        pillTextView = rootView.findViewById(R.id.toolbar_pill_text)
        pillImageView = rootView.findViewById(R.id.toolbar_pill_image)
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

        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.addPrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // Pill (smart shortcut)
    // -------------------------------------------------------------------------

    /**
     * Update the pill display based on current state:
     *  - If there is a text selection: show "Copy"
     *  - Else if clipboard has image: show thumbnail + "Ảnh"
     *  - Else if clipboard has text: show preview
     *  - Else: show "Clipboard" (default empty state)
     */
    fun refreshPill() {
        val pillText = pillTextView ?: return
        val pillImg = pillImageView

        when {
            !currentSelectionText.isNullOrEmpty() -> {
                pillImg?.visibility = View.GONE
                pillText.text = "Copy"
            }
            systemClipImageUri != null -> {
                pillImg?.visibility = View.VISIBLE
                if (systemClipImageThumbnail != null && !systemClipImageThumbnail!!.isRecycled) {
                    pillImg?.setImageBitmap(systemClipImageThumbnail)
                } else {
                    pillImg?.setImageResource(R.drawable.ic_image)
                }
                pillText.text = "Ảnh"
            }
            systemClipPreview != null -> {
                pillImg?.visibility = View.GONE
                val preview = systemClipPreview!!.take(30).replace('\n', ' ')
                pillText.text = if (systemClipPreview!!.length > 30) "$preview…" else preview
            }
            else -> {
                pillImg?.visibility = View.GONE
                pillText.text = "Clipboard"
            }
        }
    }

    /**
     * Attempt to read the current system clipboard.
     * This evaluates whether the clip has already been consumed and skips it if so.
     * We NEVER save this automatically — only used for the pill preview.
     */
    fun refreshPillFromSystemClipboard() {
        if (!prefs.isClipboardPreviewEnabled) {
            clearSystemClipPreview()
            refreshPill()
            return
        }

        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val desc = clip.description

                // Skip sensitive content (passwords, sensitive fields)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (desc.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) {
                        clearSystemClipPreview()
                        refreshPill()
                        return
                    }
                }

                val timestamp = desc.timestamp
                val item = clip.getItemAt(0)
                val itemUri = item?.uri
                val isImage = itemUri != null && (desc.hasMimeType("image/*") ||
                    context.contentResolver.getType(itemUri)?.startsWith("image/") == true)

                if (isImage) {
                    val uri = itemUri!!
                    val hash = uri.toString().hashCode()
                    if (isClipConsumed(timestamp, hash, currentClipSequence)) {
                        clearSystemClipPreview()
                    } else {
                        clearSystemClipPreview()
                        activeClipTimestamp = timestamp
                        activeClipHash = hash
                        activeClipSequence = currentClipSequence
                        systemClipImageUri = uri
                        systemClipImageDescription = desc
                        systemClipImageThumbnail = loadThumbnail(uri)
                    }
                } else {
                    val text = item?.coerceToText(context)?.toString()
                    if (!text.isNullOrBlank()) {
                        val hash = text.hashCode()
                        if (isClipConsumed(timestamp, hash, currentClipSequence)) {
                            clearSystemClipPreview()
                        } else {
                            clearSystemClipPreview()
                            activeClipTimestamp = timestamp
                            activeClipHash = hash
                            activeClipSequence = currentClipSequence
                            systemClipPreview = text
                        }
                    } else {
                        clearSystemClipPreview()
                    }
                }
            } else {
                clearSystemClipPreview()
            }
        } catch (_: SecurityException) {
            clearSystemClipPreview()
        } catch (_: Exception) {
            clearSystemClipPreview()
        }

        refreshPill()
    }

    private fun isClipConsumed(timestamp: Long, hash: Int, sequence: Long): Boolean {
        // If Android provides a valid timestamp (> 0)
        if (timestamp > 0L) {
            val consumedTs = if (consumedClipTimestamp > 0L) consumedClipTimestamp else prefs.consumedClipTimestamp
            return timestamp == consumedTs
        }

        // Fallback by sequence (each clip event increments sequence)
        if (sequence > 0L) {
            return sequence == consumedClipSequence
        }

        // Fallback by content hash when neither timestamp nor sequence is available
        val consumedHash = if (consumedClipContentHash != 0) consumedClipContentHash else prefs.consumedClipContentHash
        return hash != 0 && hash == consumedHash && isCurrentDismissed
    }

    private fun markCurrentClipConsumed() {
        consumedClipTimestamp = activeClipTimestamp
        consumedClipContentHash = activeClipHash
        consumedClipSequence = activeClipSequence
        isCurrentDismissed = true

        if (activeClipTimestamp > 0L) {
            prefs.consumedClipTimestamp = activeClipTimestamp
        }
        if (activeClipHash != 0) {
            prefs.consumedClipContentHash = activeClipHash
        }
    }

    private fun clearSystemClipPreview() {
        systemClipPreview = null
        systemClipImageUri = null
        systemClipImageDescription = null
        systemClipImageThumbnail?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        systemClipImageThumbnail = null
    }

    private fun loadThumbnail(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(48, 48), null)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun onPillClicked() {
        when {
            !currentSelectionText.isNullOrEmpty() -> {
                // Copy selection
                val selText = currentSelectionText!!
                copyToSystemClipboard(selText)
                prefs.addClipboardItem(selText)
                currentSelectionText = null
                onSelectionDismissed?.invoke()

                // Display newly copied text preview
                clearSystemClipPreview()
                systemClipPreview = selText
                activeClipTimestamp = System.currentTimeMillis()
                activeClipHash = selText.hashCode()
                activeClipSequence = ++currentClipSequence
                refreshPill()
            }
            systemClipImageUri != null && systemClipImageDescription != null -> {
                // Paste image
                val uri = systemClipImageUri!!
                val desc = systemClipImageDescription!!
                val success = onPasteImage(uri, desc)
                if (success) {
                    markCurrentClipConsumed()
                    clearSystemClipPreview()
                    refreshPill()
                }
            }
            systemClipPreview != null -> {
                // Paste text
                val text = systemClipPreview!!
                val success = onPasteText(text)
                if (success) {
                    // Save to Deshort history (Clipboard Manager)
                    prefs.addClipboardItem(text)
                    markCurrentClipConsumed()
                    clearSystemClipPreview()
                    refreshPill()
                }
            }
            else -> {
                // Default empty state: open Clipboard Manager
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
        } catch (_: Exception) {
            0xFFFFFFFF.toInt()
        }
    }

    // -------------------------------------------------------------------------
    // Public helpers
    // -------------------------------------------------------------------------

    fun onUserCopiedText(text: String) {
        if (text.isBlank()) return
        copyToSystemClipboard(text)
        prefs.addClipboardItem(text)
        clearSystemClipPreview()
        systemClipPreview = text
        activeClipTimestamp = System.currentTimeMillis()
        activeClipHash = text.hashCode()
        activeClipSequence = ++currentClipSequence
        refreshPill()
    }

    fun onSelectionChanged(selStart: Int, selEnd: Int) {
        // Selection text is fed by CursorSelectionController
    }

    fun reset() {
        currentSelectionText = null
        refreshPill()
    }

    fun onDestroy() {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.removePrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {}
        clearSystemClipPreview()
    }

    companion object {
        private var consumedClipTimestamp: Long = 0L
        private var consumedClipContentHash: Int = 0
        private var consumedClipSequence: Long = 0L
        private var isCurrentDismissed: Boolean = false
        private var currentClipSequence: Long = 1L
    }
}
