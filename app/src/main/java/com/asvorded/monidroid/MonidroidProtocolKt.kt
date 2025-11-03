package com.asvorded.monidroid

/**
 * Describes Monidroid protocol
 *
 * Formats describe sequences of elements which make up a single byte-array message
 *
 * Used types:
 * - **literal** - protocol's predefined string literal
 * - **string** - UTF-8 string. Length for strings is the length of the buffer, not characters count
 * - **int32(LE|BE)** - 32-bit integer with little-endian or big-endian byte order
 * - **bytes** - array of bytes
 */
object MonidroidProtocolKt {
    val DEBUG_TAG = "Monidroid Client"

    val MONITOR_PORT = 14765

    /**
     * **WELCOME Client message**
     *
     * Sent when connection with a server is establishing
     *
     * Format:
     * - **"WELCOME"**: literal
     * - **client model length**: int32LE
     * - **model**: string
     * - **screen width**: int32LE
     * - **screen height**: int32LE
     * - **hertz rate**: int32LE
     */
    val WELCOME_WORD = "WELCOME"

    /**
     * **FRAME Server message**
     *
     * Sent when the next frame is ready to display
     *
     * Format:
     * - **"FRAME"**: literal
     * - **data size**: int32LE
     * - **data**: bytes
     */
    val FRAME_WORD = "FRAME"

    /**
     * **ECHO Client message**
     *
     * Format:
     * - **"MDCLIENT_ECHO"**: literal
     */
    val CLIENT_ECHO_WORD = "MDCLIENT_ECHO"

    /**
     * **ECHO Server message**
     *
     * Sent when ECHO Client message is received
     *
     * Format:
     * - **"MDIDD_ECHO"**: literal
     * - **hostname length**: int32LE
     * - **hostname**: string
     */
    val SERVER_ECHO_WORD = "MDIDD_ECHO"
}