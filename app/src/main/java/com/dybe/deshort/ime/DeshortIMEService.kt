package com.dybe.deshort.ime

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import com.dybe.deshort.R
import com.dybe.deshort.data.DeshortPreferences
import com.dybe.deshort.ime.toolbar.ClipboardController
import com.dybe.deshort.ime.toolbar.CursorSelectionController
import com.dybe.deshort.ime.toolbar.EmojiPanelController
import com.dybe.deshort.input.TextCapitalizationHelper
import com.dybe.deshort.input.telex.TelexEngine
import com.dybe.deshort.input.telex.TelexSpaceBackspaceHandler
import android.content.ClipDescription
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.text.BreakIterator

/**
 * DeshortIMEService — the core keyboard service.
 *
 * Provides:
 *   - Full QWERTY layout with Vietnamese Telex composition via [TelexEngine]
 *   - One-shot Shift key with live visual label updates across all letter keys
 *   - Primary & Gboard-style secondary Symbol / Number layouts (?123 / =\< / ABC)
 *   - Grapheme-aware backspace deletion for composing text and editor text
 *   - Repeat-on-hold backspace deletion
 *   - Tone changes after Space and Backspace (e.g. vis + Space + Backspace + j -> vị)
 *   - Automatic word commitment on punctuation, spaces, enter, or symbol inputs
 *   - [KeyboardToolbar] with emoji, clipboard, cursor, and text-selection tools
 *   - [KeyboardThemeManager] for light / dark / system / scheduled themes
 */
class DeshortIMEService : InputMethodService() {

    companion object {
        const val BACKSPACE_INITIAL_DELAY_MS = 400L
        const val BACKSPACE_REPEAT_INTERVAL_MS = 50L

        // Primary symbols (?123)
        private val ROW_1_PRIMARY_SYMBOLS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        private val ROW_2_PRIMARY_SYMBOLS = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")")
        private val ROW_3_PRIMARY_SYMBOLS = listOf("/", "!", "\"", "'", ":", ";", "?")

        // Secondary symbols (=\<)
        private val ROW_1_SECONDARY_SYMBOLS = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")
        private val ROW_2_SECONDARY_SYMBOLS = listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}")
        private val ROW_3_SECONDARY_SYMBOLS = listOf("\\", "[", "]", "%", "©", "®", "™")
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** true = next letter key press will be uppercase, then reverts to false. */
    private var shiftActive = false

    /** true = showing numbers and symbols layout. */
    private var isSymbolMode = false

    /** 0 = Primary symbols (?123), 1 = Secondary symbols (=\<). */
    private var symbolPage = 0

    /** Button references for state and appearance updates. */
    private var shiftButton: Button? = null
    private var modeButton: Button? = null

    /** References to letter buttons to update texts dynamically on Shift/Symbol toggle. */
    private val letterButtons = mutableListOf<Button>()

    /** Store original tags and text labels for letter buttons. */
    private val originalKeyTags = mutableMapOf<Int, String>()
    private val originalKeyTexts = mutableMapOf<Int, String>()

    /** Vietnamese Telex input engine. */
    private val telexEngine = TelexEngine()

    /** Handler for tone modification after Space + Backspace. */
    private val spaceBackspaceHandler = TelexSpaceBackspaceHandler(telexEngine)

    /** User preferences. */
    private val prefs by lazy { DeshortPreferences(this) }

    /** Tracks whether at least one symbol character was typed in the current symbol mode session. */
    private var hasTypedSymbolInCurrentMode = false

    /** Handler for continuous backspace repeating. */
    private val mainHandler = Handler(Looper.getMainLooper())
    private var backspaceRunnable: Runnable? = null

    /** Keyboard root view reference for theme updates. */
    private var keyboardRootView: View? = null

    // -------------------------------------------------------------------------
    // Controllers
    // -------------------------------------------------------------------------

    private val themeManager: KeyboardThemeManager by lazy { KeyboardThemeManager(this, prefs) }

    private val clipboardController: ClipboardController by lazy {
        ClipboardController(
            context = this,
            prefs = prefs,
            onPasteText = { text -> pasteText(text) },
            onPasteImage = { uri, desc -> commitImageContent(uri, desc) },
            onSelectionDismissed = {
                cursorSelectionController.deactivateSelectionMode()
            },
            onPanelVisibilityChanged = { visible ->
                if (visible) {
                    keyboardRootView?.findViewById<View>(R.id.qwerty_container)?.visibility = View.GONE
                }
                // Restoring QWERTY visibility is handled by toggleClipboardPanel()
            }
        )
    }

    private val cursorSelectionController: CursorSelectionController by lazy {
        CursorSelectionController(
            onSelectionModeChanged = { active ->
                keyboardRootView?.let { themeManager.updateSelectionModeButton(it, active) }
            },
            onTextSelected = { text ->
                clipboardController.currentSelectionText = text
                clipboardController.refreshPill()
            }
        )
    }

    private val emojiController: EmojiPanelController by lazy {
        EmojiPanelController(
            context = this,
            prefs = prefs,
            onEmojiSelected = { emoji ->
                commitCurrentWord(currentInputConnection ?: return@EmojiPanelController)
                currentInputConnection?.commitText(emoji, 1)
            },
            onPanelVisibilityChanged = { visible ->
                if (visible) {
                    keyboardRootView?.findViewById<View>(R.id.qwerty_container)?.visibility = View.GONE
                }
                // Restoring QWERTY visibility is handled by toggleEmojiPanel()
            }
        )
    }

    // -------------------------------------------------------------------------
    // View Setup
    // -------------------------------------------------------------------------

    @SuppressLint("InflateParams")
    override fun onCreateInputView(): View {
        letterButtons.clear()
        originalKeyTags.clear()
        originalKeyTexts.clear()

        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
        val root = keyboardView.findViewById<LinearLayout>(R.id.keyboard_root)
        keyboardRootView = keyboardView

        wireButtons(root)
        bindControllers(keyboardView)
        themeManager.applyTheme(keyboardView)

        // Show or hide toolbar
        keyboardView.findViewById<View>(R.id.toolbar_row)?.visibility =
            if (prefs.isToolbarVisible) View.VISIBLE else View.GONE

        return keyboardView
    }

    private fun bindControllers(rootView: View) {
        clipboardController.bindViews(rootView)
        cursorSelectionController.bindViews(rootView) { currentInputConnection }
        emojiController.bindViews(rootView)

        // Emoji toolbar button
        rootView.findViewById<View>(R.id.toolbar_emoji)?.setOnClickListener {
            toggleEmojiPanel()
        }

        // Clipboard manager toolbar button
        rootView.findViewById<View>(R.id.toolbar_clipboard_mgr)?.setOnClickListener {
            toggleClipboardPanel()
        }
    }

    private fun toggleEmojiPanel() {
        if (emojiController.isPanelVisible()) {
            emojiController.hidePanel()
            keyboardRootView?.findViewById<View>(R.id.qwerty_container)?.visibility = View.VISIBLE
        } else {
            // Hide clipboard if visible
            if (clipboardController.isPanelVisible()) {
                clipboardController.hidePanel()
            }
            keyboardRootView?.findViewById<View>(R.id.qwerty_container)?.visibility = View.GONE
            emojiController.showPanel()
        }
    }

    private fun toggleClipboardPanel() {
        if (clipboardController.isPanelVisible()) {
            clipboardController.hidePanel()
            keyboardRootView?.findViewById<View>(R.id.qwerty_container)?.visibility = View.VISIBLE
        } else {
            if (emojiController.isPanelVisible()) {
                emojiController.hidePanel()
            }
            keyboardRootView?.findViewById<View>(R.id.qwerty_container)?.visibility = View.GONE
            clipboardController.showPanel()
        }
    }

    /**
     * Recursively walk the view hierarchy and attach click listeners to every Button.
     */
    private fun wireButtons(view: View) {
        when (view) {
            is LinearLayout -> {
                for (i in 0 until view.childCount) {
                    wireButtons(view.getChildAt(i))
                }
            }
            is Button -> {
                originalKeyTags[view.id] = view.tag as? String ?: ""
                originalKeyTexts[view.id] = view.text.toString()

                when (view.id) {
                    R.id.key_shift -> {
                        shiftButton = view
                        view.setOnClickListener { onKeyClick(view) }
                    }
                    R.id.key_mode -> {
                        modeButton = view
                        view.setOnClickListener { onKeyClick(view) }
                    }
                    R.id.key_backspace -> {
                        setupBackspaceButton(view)
                    }
                    else -> {
                        val tag = view.tag as? String
                        if (tag != null && tag.contains("|")) {
                            letterButtons.add(view)
                        }
                        view.setOnClickListener { onKeyClick(view) }
                    }
                }
            }
        }
    }

    /**
     * Set up backspace button touch listener for repeat-on-hold behavior.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupBackspaceButton(button: Button) {
        button.setOnClickListener {
            performBackspace()
        }
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    performBackspace()
                    startBackspaceRepeat()
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopBackspaceRepeat()
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    // -------------------------------------------------------------------------
    // Key handling
    // -------------------------------------------------------------------------

    /**
     * Perform a single grapheme-aware backspace action.
     */
    fun performBackspace() {
        val ic = currentInputConnection ?: return
        if (!telexEngine.isEmpty) {
            spaceBackspaceHandler.reset()
            val newComposed = telexEngine.backspace()
            if (newComposed.isEmpty()) {
                ic.setComposingText("", 0)
                ic.finishComposingText()
            } else {
                ic.setComposingText(newComposed, 1)
            }
        } else {
            val beforeText = ic.getTextBeforeCursor(64, 0)?.toString()

            if (spaceBackspaceHandler.onBackspace(beforeText)) {
                ic.deleteSurroundingText(1, 0)
                updateAutoCapitalization()
                return
            }

            if (!beforeText.isNullOrEmpty()) {
                val boundary = BreakIterator.getCharacterInstance()
                boundary.setText(beforeText)
                val end = boundary.last()
                val start = boundary.previous()
                val charsToDelete = if (start != BreakIterator.DONE && end > start) {
                    end - start
                } else {
                    1
                }
                ic.deleteSurroundingText(charsToDelete, 0)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }
        updateAutoCapitalization()
    }

    fun startBackspaceRepeat() {
        stopBackspaceRepeat()
        val runnable = object : Runnable {
            override fun run() {
                performBackspace()
                mainHandler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS)
            }
        }
        backspaceRunnable = runnable
        mainHandler.postDelayed(runnable, BACKSPACE_INITIAL_DELAY_MS)
    }

    fun stopBackspaceRepeat() {
        backspaceRunnable?.let {
            mainHandler.removeCallbacks(it)
            backspaceRunnable = null
        }
    }

    /**
     * Paste text at the current cursor position.
     * Commits any composing word first to avoid interference with TelexEngine.
     * Returns true if paste succeeded, false otherwise.
     */
    private fun pasteText(text: String): Boolean {
        val ic = currentInputConnection ?: return false
        commitCurrentWord(ic)
        return ic.commitText(text, 1)
    }

    /**
     * Insert image via commitContent() API.
     * Validates target editor MIME type support, returns true if insertion succeeds.
     */
    private fun commitImageContent(uri: Uri, description: ClipDescription): Boolean {
        val ic = currentInputConnection ?: return false
        val ei = currentInputEditorInfo ?: return false

        val contentMimeTypes = EditorInfoCompat.getContentMimeTypes(ei)
        val matchingMime = contentMimeTypes.firstOrNull { mime -> description.hasMimeType(mime) }

        if (matchingMime == null) {
            Toast.makeText(this, "Ứng dụng này không hỗ trợ dán ảnh", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val inputContentInfo = InputContentInfoCompat(
                uri,
                ClipDescription(description.label, arrayOf(matchingMime)),
                null
            )
            val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            val result = InputConnectionCompat.commitContent(ic, ei, inputContentInfo, flags, null)
            if (!result) {
                Toast.makeText(this, "Không thể dán ảnh vào trường này", Toast.LENGTH_SHORT).show()
            }
            result
        } catch (e: Exception) {
            Log.e("DeshortIMEService", "commitContent failed: ${e.message}", e)
            Toast.makeText(this, "Không thể dán ảnh", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Handles a key click.
     */
    private fun onKeyClick(button: Button) {
        val ic = currentInputConnection ?: return
        val tag = button.tag as? String ?: return

        // If emoji or clipboard panel is open, close it first on any key press
        if (emojiController.isPanelVisible() || clipboardController.isPanelVisible()) {
            emojiController.hidePanel()
            clipboardController.hidePanel()
            keyboardRootView?.findViewById<View>(R.id.qwerty_container)?.visibility = View.VISIBLE
            // Don't process the key click itself
            return
        }

        // Deactivate selection mode on any regular key press
        if (cursorSelectionController.isSelectionModeActive) {
            when (tag) {
                // Allow selection mode to be toggled off
                else -> cursorSelectionController.deactivateSelectionMode()
            }
        }

        when (tag) {
            "BACKSPACE" -> {
                performBackspace()
            }

            "SPACE" -> {
                if (isSymbolMode && prefs.isAutoSwitchBackAbcEnabled && hasTypedSymbolInCurrentMode) {
                    ic.commitText(" ", 1)
                    isSymbolMode = false
                    symbolPage = 0
                    hasTypedSymbolInCurrentMode = false
                    updateSymbolModeLayout()
                    updateAutoCapitalization()
                    return
                }

                ic.beginBatchEdit()
                try {
                    if (!telexEngine.isEmpty) {
                        val composedWord = telexEngine.compose()
                        commitCurrentWord(ic)
                        ic.commitText(" ", 1)
                        spaceBackspaceHandler.onSpace(composedWord)
                    } else {
                        commitCurrentWord(ic)
                        ic.commitText(" ", 1)
                        spaceBackspaceHandler.onSpace(null)
                    }
                } finally {
                    ic.endBatchEdit()
                }
                updateAutoCapitalization()
            }

            "SHIFT" -> {
                spaceBackspaceHandler.reset()
                if (!isSymbolMode) {
                    shiftActive = !shiftActive
                    updateShiftAppearance()
                }
            }

            "SYM_TOGGLE" -> {
                spaceBackspaceHandler.reset()
                if (isSymbolMode) {
                    symbolPage = if (symbolPage == 0) 1 else 0
                    updateSymbolModeLayout()
                }
            }

            "MODE_SYM" -> {
                spaceBackspaceHandler.reset()
                isSymbolMode = !isSymbolMode
                symbolPage = 0
                hasTypedSymbolInCurrentMode = false
                shiftActive = false
                updateSymbolModeLayout()
                if (!isSymbolMode) {
                    updateAutoCapitalization()
                }
            }

            "ENTER" -> {
                spaceBackspaceHandler.reset()
                commitCurrentWord(ic)

                val ei = currentInputEditorInfo
                val imeAction = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE

                if (imeAction != EditorInfo.IME_ACTION_NONE &&
                    imeAction != EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
                    ic.performEditorAction(imeAction)
                } else {
                    ic.commitText("\n", 1)
                }
                updateAutoCapitalization()
            }

            else -> {
                val parts = tag.split("|")
                if (parts.size == 2) {
                    val typedChar = if (shiftActive && !isSymbolMode) parts[1][0] else parts[0][0]

                    if (isSymbolMode) {
                        hasTypedSymbolInCurrentMode = true
                        spaceBackspaceHandler.reset()
                        commitCurrentWord(ic)
                        ic.commitText(typedChar.toString(), 1)
                        return
                    }

                    if (typedChar.isLetter()) {
                        val beforeText = ic.getTextBeforeCursor(64, 0)?.toString()
                        val toneChange = spaceBackspaceHandler.onKey(typedChar, beforeText)
                        if (toneChange != null) {
                            val (oldWord, newWord) = toneChange
                            ic.beginBatchEdit()
                            try {
                                ic.deleteSurroundingText(oldWord.length, 0)
                                ic.setComposingText(newWord, 1)
                            } finally {
                                ic.endBatchEdit()
                            }

                            if (shiftActive) {
                                shiftActive = false
                                updateShiftAppearance()
                            }
                            return
                        }

                        // Normal letter key: feed into Telex engine
                        val composed = telexEngine.type(typedChar)
                        ic.setComposingText(composed, 1)

                        if (shiftActive) {
                            shiftActive = false
                            updateShiftAppearance()
                        }
                    } else {
                        spaceBackspaceHandler.reset()
                        commitCurrentWord(ic)
                        ic.commitText(typedChar.toString(), 1)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Word commitment
    // -------------------------------------------------------------------------

    private fun commitCurrentWord(ic: android.view.inputmethod.InputConnection) {
        if (!telexEngine.isEmpty) {
            ic.finishComposingText()
            telexEngine.reset()
        }
    }

    // -------------------------------------------------------------------------
    // Layout and Appearance Updates
    // -------------------------------------------------------------------------

    private fun updateShiftAppearance() {
        shiftButton?.let { btn ->
            btn.isSelected = shiftActive
            themeManager.updateShiftButton(btn, shiftActive)
        }

        letterButtons.forEach { button ->
            val origTag = originalKeyTags[button.id] ?: return@forEach
            val parts = origTag.split("|")
            if (parts.size == 2) {
                button.text = if (shiftActive) parts[1] else parts[0]
            }
        }
    }

    private fun updateSymbolModeLayout() {
        if (isSymbolMode) {
            modeButton?.text = "ABC"

            shiftButton?.apply {
                tag = "SYM_TOGGLE"
                isEnabled = true
                isSelected = false
                alpha = 1.0f
                text = if (symbolPage == 0) "=\\<" else "?123"
            }

            val row1Keys = listOf(
                R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r, R.id.key_t,
                R.id.key_y, R.id.key_u, R.id.key_i, R.id.key_o, R.id.key_p
            )
            val row2Keys = listOf(
                R.id.key_a, R.id.key_s, R.id.key_d, R.id.key_f, R.id.key_g,
                R.id.key_h, R.id.key_j, R.id.key_k, R.id.key_l
            )
            val row3Keys = listOf(
                R.id.key_z, R.id.key_x, R.id.key_c, R.id.key_v, R.id.key_b,
                R.id.key_n, R.id.key_m
            )

            val r1Symbols = if (symbolPage == 0) ROW_1_PRIMARY_SYMBOLS else ROW_1_SECONDARY_SYMBOLS
            val r2Symbols = if (symbolPage == 0) ROW_2_PRIMARY_SYMBOLS else ROW_2_SECONDARY_SYMBOLS
            val r3Symbols = if (symbolPage == 0) ROW_3_PRIMARY_SYMBOLS else ROW_3_SECONDARY_SYMBOLS

            row1Keys.forEachIndexed { i, id ->
                letterButtons.find { it.id == id }?.apply {
                    val sym = r1Symbols.getOrElse(i) { "" }
                    tag = "$sym|$sym"
                    text = sym
                }
            }
            row2Keys.forEachIndexed { i, id ->
                letterButtons.find { it.id == id }?.apply {
                    val sym = r2Symbols.getOrElse(i) { "" }
                    tag = "$sym|$sym"
                    text = sym
                }
            }
            row3Keys.forEachIndexed { i, id ->
                letterButtons.find { it.id == id }?.apply {
                    val sym = r3Symbols.getOrElse(i) { "" }
                    tag = "$sym|$sym"
                    text = sym
                }
            }
        } else {
            modeButton?.text = "?123"

            shiftButton?.apply {
                tag = "SHIFT"
                text = "⇧"
                isEnabled = true
                alpha = 1.0f
            }

            letterButtons.forEach { button ->
                val origTag = originalKeyTags[button.id] ?: return@forEach
                val origText = originalKeyTexts[button.id] ?: return@forEach
                button.tag = origTag
                button.text = origText
            }
            updateShiftAppearance()
        }
    }

    // -------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Note: Do NOT reset spaceBackspaceHandler here because Android fires onUpdateSelection
        // asynchronously for the IME's own commitText and deleteSurroundingText calls.
        if (candidatesStart < 0 && candidatesEnd < 0 && !telexEngine.isEmpty) {
            telexEngine.reset()
        }

        // Notify cursor controller of external selection changes
        cursorSelectionController.onExternalSelectionUpdate(newSelStart, newSelEnd)

        // Update clipboard pill based on selection
        val hasSelection = newSelStart != newSelEnd
        if (!hasSelection && clipboardController.currentSelectionText != null) {
            clipboardController.currentSelectionText = null
            clipboardController.refreshPill()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        stopBackspaceRepeat()
        spaceBackspaceHandler.reset()
        hasTypedSymbolInCurrentMode = false
        shiftActive = false
        isSymbolMode = false
        symbolPage = 0
        updateSymbolModeLayout()
        telexEngine.reset()
        cursorSelectionController.reset()
        clipboardController.reset()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateAutoCapitalization()

        // Apply theme (handles scheduled theme without background service)
        keyboardRootView?.let { themeManager.applyTheme(it) }

        // Attempt to read clipboard for pill preview (may return null on Android 10+ if not focused)
        clipboardController.refreshPillFromSystemClipboard()
    }

    private fun updateAutoCapitalization() {
        if (!prefs.isAutoCapitalizationEnabled || isSymbolMode) return
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(64, 0)?.toString()
        val shouldShift = TextCapitalizationHelper.isSentenceStart(textBefore)
        if (shiftActive != shouldShift) {
            shiftActive = shouldShift
            updateShiftAppearance()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        stopBackspaceRepeat()
        spaceBackspaceHandler.reset()
        val ic = currentInputConnection
        if (ic != null && !telexEngine.isEmpty) {
            ic.finishComposingText()
        }
        telexEngine.reset()
        cursorSelectionController.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackspaceRepeat()
        spaceBackspaceHandler.reset()
        mainHandler.removeCallbacksAndMessages(null)
        cursorSelectionController.onDestroy()
        clipboardController.onDestroy()
        shiftButton = null
        modeButton = null
        letterButtons.clear()
        originalKeyTags.clear()
        originalKeyTexts.clear()
        keyboardRootView = null
    }
}
