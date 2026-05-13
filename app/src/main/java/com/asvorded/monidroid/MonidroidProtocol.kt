package com.asvorded.monidroid

/**
 * Describes Monidroid protocol
 *
 * Formats describe sequences of elements which
 * together with word make up a single byte-array message
 *
 * Types remarks:
 * - Strings are UTF-8
 * - multibyte integers are little-endian
 * - length of string does not include '\0'
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
    const val ADB_PORT = 14767

    const val WORD_LEN = 5

    /**
     * **WELCOME Client message**
     *
     * Sent when connection with a server is establishing
     *
     * **CWLCM** - for Wi-Fi connections, **CUSBW** - fow USB connections
     *
     * Format: <client model length(int)><model(string)><screen width(int)>
     *     <screen height(int)><refresh rate(int)>
     */
    const val WELCOME_WORD = "CWLCM"
    const val USB_WELCOME_WORD = "CUSBW"

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
     * **INPUT Client message**
     *
     * Sent after screen input
     *
     * Type 1 format (mouse move): <1(byte)><X offset(int)><Y offset(int)>
     *
     * Type 2 format (mouse buttons): <2(byte)><button flags(byte)>
     *
     * Type 3 format (mouse scroll): <3(byte)><scroll offset(int)>
     *
     * Type 5 format (touch input): <5(byte)><fingers count(byte)><fingers(finger[])>,
     *      where finger: <finger id(int)><X(int)><Y(int)>
     *      and X, Y are absolute and scaled to screen dimensions
     */
    const val INPUT_WORD = "CINPT"

    enum class InputType(val code: Int) {
        MouseMove    (1),
        MouseButtons (2),
        MouseScroll  (3),

        Mouse        (4),

        Touch        (5),
    }

    object MouseFlags {
        const val LButton = 1 shl 0
        const val RButton = 1 shl 1
        const val MButton = 1 shl 2
    }

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