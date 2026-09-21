package com.digitalmunshi.pos.core.hardware.escpos

object EscPosCommands {

    // Standard ESC/POS Control Characters
    const val ESC: Byte = 0x1B
    const val FS: Byte = 0x1C
    const val GS: Byte = 0x1D
    const val DLE: Byte = 0x10
    const val LF: Byte = 0x0A
    const val CR: Byte = 0x0D

    // Initialization
    val INIT_PRINTER = byteArrayOf(ESC, 0x40)

    // Alignment
    val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(ESC, 0x61, 0x02)

    // Typography
    val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)
    val DOUBLE_HEIGHT_ON = byteArrayOf(GS, 0x21, 0x01)
    val DOUBLE_WIDTH_ON = byteArrayOf(GS, 0x21, 0x10)
    val DOUBLE_SIZE_ON = byteArrayOf(GS, 0x21, 0x11)
    val TEXT_NORMAL = byteArrayOf(GS, 0x21, 0x00)
    val UNDERLINE_ON = byteArrayOf(ESC, 0x2D, 0x01)
    val UNDERLINE_OFF = byteArrayOf(ESC, 0x2D, 0x00)

    // Paper Feeding & Cutting
    val LINE_FEED = byteArrayOf(LF)
    val FEED_3_LINES = byteArrayOf(ESC, 0x64, 0x03)
    val FEED_5_LINES = byteArrayOf(ESC, 0x64, 0x05)
    val PAPER_FULL_CUT = byteArrayOf(GS, 0x56, 0x00)
    val PAPER_PARTIAL_CUT = byteArrayOf(GS, 0x56, 0x42, 0x00)

    /**
     * Standard Cash Drawer Kick Pulse:
     * ESC p m t1 t2
     * 0x1B, 0x70, 0x00, 0x19, 0xFA
     * m = 0 (Pin 2), t1 = 25 (50ms on), t2 = 250 (500ms off)
     */
    val DRAWER_KICK_PULSE = byteArrayOf(
        0x1B.toByte(),
        0x70.toByte(),
        0x00.toByte(),
        0x19.toByte(),
        0xFA.toByte()
    )
}
