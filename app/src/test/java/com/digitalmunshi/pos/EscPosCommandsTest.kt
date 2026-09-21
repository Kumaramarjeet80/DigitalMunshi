package com.digitalmunshi.pos

import com.digitalmunshi.pos.core.hardware.escpos.EscPosCommands
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class EscPosCommandsTest {

    @Test
    fun `verify standard cash drawer kick pulse bytes`() {
        val expectedPulse = byteArrayOf(
            0x1B.toByte(), // ESC
            0x70.toByte(), // 'p'
            0x00.toByte(), // Pin 2
            0x19.toByte(), // t1 = 25 * 2ms = 50ms
            0xFA.toByte()  // t2 = 250 * 2ms = 500ms
        )

        assertArrayEquals(
            "Cash drawer pulse must strictly match ESC p 0 25 250 (0x1B, 0x70, 0x00, 0x19, 0xFA)",
            expectedPulse,
            EscPosCommands.DRAWER_KICK_PULSE
        )
    }

    @Test
    fun `verify paper cut commands`() {
        val expectedPartialCut = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
        assertArrayEquals(expectedPartialCut, EscPosCommands.PAPER_PARTIAL_CUT)
    }
}
