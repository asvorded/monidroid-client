package com.asvorded.monidroid

/**
 * Describes Monidroid protocol
 *
 * Formats describe sequences of elements which make up a single byte-array message
 *
 * Used types:
 * - **literal** - protocol's predefined string literal
 * - **string** - UTF-8 string.
 * - **int** - 32-bit little-endian integer
 * - **bytes** - array of bytes
 *
 * Definitions:
 * - **length of string** - count of characters **excluding** '\0'
 */
object MonidroidProtocol {
    enum class ErrorCode(val code: Int) {
        MessageEncoded            (0),

        NotIdentified             (1),
        IncorrectMonitorOptions   (2),
        InvalidClient             (3),

        DisconnectedByServer      (10),
        MonitorConnectFail        (11),
        TooManyFails              (12),

        Unspecified               (100),
    }

    const val DEBUG_TAG = "Monidroid Client"

    const val PROTOCOL_PORT = 14770

    const val WORD_LEN = 5

    /**
     * **WELCOME Client message**
     *
     * Sent when connection with a server is establishing
     *
     * Format: <client model length(int)><model(string)><screen width(int)>
     *     <screen height(int)><refresh rate(int)>
     */
    const val WELCOME_WORD = "CWLCM"

    /**
     * ** STREAM Server message
     *
     * Sent when server enables streaming instead of sending full frames
     *
     * Format: TODO: make format
     */
    const val SV_STREAM_WORD = "SSTRM"

    /**
     * **FRAME Server message**
     *
     * Sent when the next frame is ready to display
     *
     * Format: <data length(int)><data(byte[])>
     */
    const val SV_FRAME_WORD = "SFRME"

    /**
     * **ERROR Server message**
     *
     * Sent when error in server occurred
     *
     * Format: <code(int)><message length(int)><message(string)>
     */
    const val SV_ERROR_WORD = "SERRC"

    /**
     * **ECHO Client message**
     *
     * Format: none
     */
    const val ECHO_WORD = "CECHO"

    /**
     * **ECHO Server message**
     *
     * Format: <3-letter OS ID(string)><hostname length(int)><hostname(string)>
     */
    const val SV_ECHO_WORD = "SECHO"
    const val OS_ID_LEN = 3

    enum class OsId(val id: String) {
        UNKNOWN       ("UNK"),

        WINDOWS       ("WIN"),

        GENERIC_LINUX ("GNU"),
        UBUNTU        ("UBU"),
        KUBUNTU       ("KBU"),
        XUBUNTU       ("XBU"),
        LUBUNTU       ("LBU"),
        DEBIAN        ("DEB"),
        ZORIN         ("ZOR"),
        POP_OS        ("POP"),
        ARCH_LINUX    ("ARC"),
        CACHYOS       ("CHY"),
        BAZZITE       ("BAZ"),
        MANJARO       ("MAJ"),
        FEDORA        ("FED");

        companion object {
            fun fromId(id: String): OsId {
                return entries.find { entry -> entry.id == id } ?: UNKNOWN
            }
        }
    }

    object Extensions {

    }
}