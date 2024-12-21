package com.example.myapplication;

public class MonidroidProtocol {
    public static String DEBUG_TAG = "Monidroid Client";

    public static int MONITOR_PORT = 14765;

    /**
    * WELCOME
    * Message format: WELCOME[model_length][model][screen_sides][hertz]
    */
    public static String WELCOME_WORD = "WELCOME";

    /**
     * FRAME
     * Message format: FRAME[size][data]
     */
    public static String FRAME_WORD = "FRAME";

    /**
     * ECHO Client message format: MDCLIENT_ECHO
     */
    public static String CLIENT_ECHO_WORD = "MDCLIENT_ECHO";
    /**
     * ECHO Server message format: MDIDD_ECHO[model_length][model]
     */
    public static String SERVER_ECHO_WORD = "MDIDD_ECHO";
}
