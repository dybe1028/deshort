package com.dybe.deshort.ime.toolbar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardShortcutLogicTest {

    private class ShortcutStateSimulator {
        var consumedTimestamp: Long = 0L
        var consumedHash: Int = 0
        var consumedSequence: Long = 0L
        var isCurrentDismissed: Boolean = false
        var currentSequence: Long = 1L

        fun isClipConsumed(timestamp: Long, hash: Int, sequence: Long): Boolean {
            if (timestamp > 0L) {
                return timestamp == consumedTimestamp
            }
            if (sequence > 0L) {
                return sequence == consumedSequence
            }
            return hash != 0 && hash == consumedHash && isCurrentDismissed
        }

        fun markConsumed(timestamp: Long, hash: Int, sequence: Long) {
            consumedTimestamp = timestamp
            consumedHash = hash
            consumedSequence = sequence
            isCurrentDismissed = true
        }
    }

    @Test
    fun testCopyPasteDismissReopenLifecycle() {
        val state = ShortcutStateSimulator()

        // 1. Copy A at t = 1000
        val clipATimestamp = 1000L
        val clipAHash = "Hello World".hashCode()
        assertFalse("New clip A should not be consumed", state.isClipConsumed(clipATimestamp, clipAHash, state.currentSequence))

        // 2. User taps paste -> paste succeeds -> mark consumed
        state.markConsumed(clipATimestamp, clipAHash, state.currentSequence)
        assertTrue("Clip A should now be consumed", state.isClipConsumed(clipATimestamp, clipAHash, state.currentSequence))

        // 3. Reopening keyboard: system clipboard still has clip A at t = 1000
        assertTrue("Reopening keyboard must keep consumed clip A hidden", state.isClipConsumed(clipATimestamp, clipAHash, state.currentSequence))

        // 4. Copy B at t = 2000
        val clipBTimestamp = 2000L
        val clipBHash = "New Content".hashCode()
        state.currentSequence++
        assertFalse("Genuinely new clip B must be shown", state.isClipConsumed(clipBTimestamp, clipBHash, state.currentSequence))

        // 5. User taps paste for B -> mark consumed
        state.markConsumed(clipBTimestamp, clipBHash, state.currentSequence)
        assertTrue("Clip B is now consumed", state.isClipConsumed(clipBTimestamp, clipBHash, state.currentSequence))

        // 6. User copies A again at t = 3000
        val clipA2Timestamp = 3000L
        state.currentSequence++
        assertFalse("Copying A again creates a new event and must be shown", state.isClipConsumed(clipA2Timestamp, clipAHash, state.currentSequence))
    }

    @Test
    fun testZeroTimestampFallbackSequence() {
        val state = ShortcutStateSimulator()

        // If Android provides timestamp = 0
        val clipHash = "Text".hashCode()
        assertFalse("Initial clip should not be consumed", state.isClipConsumed(0L, clipHash, state.currentSequence))

        // User pastes -> mark consumed
        state.markConsumed(0L, clipHash, state.currentSequence)
        assertTrue("Clip should be consumed", state.isClipConsumed(0L, clipHash, state.currentSequence))

        // Reopening without new copy (same sequence)
        assertTrue("Reopened should remain consumed", state.isClipConsumed(0L, clipHash, state.currentSequence))

        // User copies again -> OnPrimaryClipChangedListener increments sequence
        state.currentSequence++
        assertFalse("New copy event with incremented sequence must be shown", state.isClipConsumed(0L, clipHash, state.currentSequence))
    }
}
