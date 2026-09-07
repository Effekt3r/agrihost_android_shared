package za.co.mjrsolutions.shared.language

enum class CommonPhrase : LangKey {
    YES, NO, OK, CANCEL, ACCEPT, SKIP,
    LOADING, SAVING, ERROR, RETRY,
    INFORMATION_NEEDED, NOTHING_TO_SHOW, NETWORK_UNAVAILABLE,
    LOGIN, LOGOUT, USERNAME, PASSWORD, INVALID_LOGIN,
    APP_UPDATE_AVAILABLE, UPDATE_REQUIRED, UPDATE_BUTTON, LATER,
    CHANGE_LANGUAGE, DEFAULT_SPINNER_VALUE,

    // Server-connectivity banner / blocked-action dialog (:connectivity). One key per
    // reason the server cannot be reached. The key names are the dictionary contract the
    // hael af/en/en-us.json files already carry, so they are NOT renamed to English.
    GEEN_NETWERK_VERBINDING,      // wifi and mobile data both off
    GEEN_INTERNET_TOEGANG,        // attached to a network, but no route to the internet
    GEEN_VERBINDING_MET_BEDIENER, // the internet works, our base URL does not answer
    VERBINDING_HERSTEL,           // connection is back
    GEEN_BEDIENER_VERBINDING;     // dialog title when a server-bound action is blocked

    override fun key(): String = name
}
