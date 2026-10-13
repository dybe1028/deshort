package com.dybe.deshort.ime.toolbar

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.dybe.deshort.R
import java.text.BreakIterator

/**
 * CursorSelectionController — handles cursor movement and text selection.
 *
 * Cursor movement is grapheme-aware: it will NOT split surrogate pairs or
 * combining character sequences. Uses BreakIterator.getCharacterInstance() for
 * safe boundary detection.
 *
 * Selection mode: hold the anchor at the position when mode was activated, then
 * extend the selection extent left/right on each arrow press.
 */
class CursorSelectionController(
    private val onSelectionModeChanged: (active: Boolean) -> Unit,
    private val onTextSelected: (text: String?) -> Unit
) {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Whether text selection mode is currently active */
    var isSelectionModeActive: Boolean = false
        private set

    /** Selection anchor position (fixed end) when in selection mode */
    private var selectionAnchor: Int = -1

    private val mainHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    companion object {
        private const val CURSOR_INITIAL_DELAY_MS = 400L
        private const val CURSOR_REPEAT_INTERVAL_MS = 60L
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    fun bindViews(
        rootView: View,
        getInputConnection: () -> InputConnection?
    ) {
        val leftBtn = rootView.findViewById<View>(R.id.toolbar_cursor_left)
        val rightBtn = rootView.findViewById<View>(R.id.toolbar_cursor_right)
        val selectBtn = rootView.findViewById<View>(R.id.toolbar_text_select)

        leftBtn?.setOnClickListener {
            getInputConnection()?.let { moveCursorLeft(it) }
        }
        leftBtn?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRepeat { getInputConnection()?.let { moveCursorLeft(it) } }
                    false // Let click listener also fire
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRepeat()
                    false
                }
                else -> false
            }
        }

        rightBtn?.setOnClickListener {
            getInputConnection()?.let { moveCursorRight(it) }
        }
        rightBtn?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRepeat { getInputConnection()?.let { moveCursorRight(it) } }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRepeat()
                    false
                }
                else -> false
            }
        }

        selectBtn?.setOnClickListener {
            getInputConnection()?.let { ic ->
                toggleSelectionMode(ic)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mode toggle
    // -------------------------------------------------------------------------

    fun toggleSelectionMode(ic: InputConnection) {
        if (isSelectionModeActive) {
            deactivateSelectionMode()
        } else {
            activateSelectionMode(ic)
        }
    }

    private fun activateSelectionMode(ic: InputConnection) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        selectionAnchor = extracted.selectionStart
        isSelectionModeActive = true
        onSelectionModeChanged(true)
        onTextSelected(null)
    }

    fun deactivateSelectionMode() {
        isSelectionModeActive = false
        selectionAnchor = -1
        onSelectionModeChanged(false)
        onTextSelected(null)
    }

    // -------------------------------------------------------------------------
    // Cursor movement
    // -------------------------------------------------------------------------

    fun moveCursorLeft(ic: InputConnection) {
        if (isSelectionModeActive) {
            extendSelectionLeft(ic)
        } else {
            moveCursorLeftNormal(ic)
        }
    }

    fun moveCursorRight(ic: InputConnection) {
        if (isSelectionModeActive) {
            extendSelectionRight(ic)
        } else {
            moveCursorRightNormal(ic)
        }
    }

    // -------------------------------------------------------------------------
    // Normal cursor movement (no selection)
    // -------------------------------------------------------------------------

    private fun moveCursorLeftNormal(ic: InputConnection) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: run {
            // Fallback: use sendKeyEvent left
            ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
            ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
            return
        }
        val text = extracted.text?.toString() ?: return
        val cursorPos = extracted.selectionStart
        if (cursorPos <= 0) return

        val newPos = prevGraphemeClusterBoundary(text, cursorPos)
        ic.setSelection(newPos, newPos)
    }

    private fun moveCursorRightNormal(ic: InputConnection) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: run {
            ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
            ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
            return
        }
        val text = extracted.text?.toString() ?: return
        val cursorPos = extracted.selectionStart
        if (cursorPos >= text.length) return

        val newPos = nextGraphemeClusterBoundary(text, cursorPos)
        ic.setSelection(newPos, newPos)
    }

    // -------------------------------------------------------------------------
    // Selection extension
    // -------------------------------------------------------------------------

    private fun extendSelectionLeft(ic: InputConnection) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val text = extracted.text?.toString() ?: return
        val cursorPos = extracted.selectionStart
        if (cursorPos <= 0) return

        val newExtent = prevGraphemeClusterBoundary(text, cursorPos)
        val anchor = selectionAnchor.coerceIn(0, text.length)

        if (newExtent <= anchor) {
            // Normal selection: extent is left of anchor
            ic.setSelection(newExtent, anchor)
        } else {
            // Reverse selection shrink: extent is right of anchor
            ic.setSelection(anchor, newExtent)
        }
        notifySelectionText(ic)
    }

    private fun extendSelectionRight(ic: InputConnection) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val text = extracted.text?.toString() ?: return
        val cursorPos = extracted.selectionEnd
        if (cursorPos >= text.length) return

        val newExtent = nextGraphemeClusterBoundary(text, cursorPos)
        val anchor = selectionAnchor.coerceIn(0, text.length)

        if (newExtent >= anchor) {
            // Normal selection: extent is right of anchor
            ic.setSelection(anchor, newExtent)
        } else {
            // Reverse: extent is left of anchor
            ic.setSelection(newExtent, anchor)
        }
        notifySelectionText(ic)
    }

    private fun notifySelectionText(ic: InputConnection) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val text = extracted.text?.toString() ?: return
        val start = extracted.selectionStart
        val end = extracted.selectionEnd
        val selected = if (start < end) text.substring(start, end) else null
        onTextSelected(selected)
    }

    // -------------------------------------------------------------------------
    // Grapheme-aware boundary helpers
    // -------------------------------------------------------------------------

    private fun prevGraphemeClusterBoundary(text: String, pos: Int): Int {
        if (pos <= 0) return 0
        val bi = BreakIterator.getCharacterInstance()
        bi.setText(text)
        return bi.preceding(pos).let { if (it == BreakIterator.DONE) 0 else it }
    }

    private fun nextGraphemeClusterBoundary(text: String, pos: Int): Int {
        if (pos >= text.length) return text.length
        val bi = BreakIterator.getCharacterInstance()
        bi.setText(text)
        return bi.following(pos).let { if (it == BreakIterator.DONE) text.length else it }
    }

    // -------------------------------------------------------------------------
    // Repeat on hold
    // -------------------------------------------------------------------------

    private fun startRepeat(action: () -> Unit) {
        stopRepeat()
        val r = object : Runnable {
            override fun run() {
                action()
                mainHandler.postDelayed(this, CURSOR_REPEAT_INTERVAL_MS)
            }
        }
        repeatRunnable = r
        mainHandler.postDelayed(r, CURSOR_INITIAL_DELAY_MS)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { mainHandler.removeCallbacks(it) }
        repeatRunnable = null
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    fun reset() {
        stopRepeat()
        if (isSelectionModeActive) {
            deactivateSelectionMode()
        }
    }

    fun onDestroy() {
        stopRepeat()
        mainHandler.removeCallbacksAndMessages(null)
    }

    /** Called by DeshortIMEService.onUpdateSelection */
    fun onExternalSelectionUpdate(newSelStart: Int, newSelEnd: Int) {
        if (!isSelectionModeActive) return
        // Keep anchor valid — if selection was collapsed externally, leave selection mode
        if (newSelStart == newSelEnd && selectionAnchor != newSelStart) {
            // User tapped elsewhere — exit selection mode
            deactivateSelectionMode()
        }
    }
}
