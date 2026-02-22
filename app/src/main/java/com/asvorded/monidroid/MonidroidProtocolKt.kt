package com.asvorded.monidroid

/**
 * Describes Monidroid protocol
 *
 * Formats describe sequences of elements which make up a single byte-array message
 *
 * Used types:
 * - **literal** - protocol's predefined string literal
 * - **string** - UTF-16LE string.
 * - **int** - 32-bit little-endian integer
 * - **bytes** - array of bytes
 *
 * Definitions:
 * - **length of string** - count of characters **excluding** '\0'
 * - **size of string** - size of the buffer of a string (**including** '\0')
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
     * - **length of client model**: int
     * - **model**: string
     * - **screen width**: int
     * - **screen height**: int
     * - **hertz rate**: int
     */
    val WELCOME_WORD = "WELCOME"

    /**
     * **FRAME Server message**
     *
     * Sent when the next frame is ready to display
     *
     * Format:
     * - **"FRAME"**: literal
     * - **data size**: int
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
     * - **length of hostname**: int
     * - **hostname**: string
     */
    val SERVER_ECHO_WORD = "MDIDD_ECHO"
}