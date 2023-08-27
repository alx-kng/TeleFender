package com.telefender.phone.data.server_related


enum class ServerMode(val serverString: String, val urlPart: String) {
    DEV("dev","dev."),
    TEST("test","test."),
    STAGE("stage","stage."),
    PROD("prod","")
}

/**
 * Converts serverStr to ServerMode if possible.
 */
fun String.toServerModeFromServerStr() : ServerMode? {
    for (serverMode in ServerMode.values()) {
        if (this == serverMode.serverString) {
            return serverMode
        }
    }

    return null
}

/**
 * Converts urlPart to ServerMode if possible.
 */
fun String.toServerModeFromUrlPart() : ServerMode? {
    for (serverMode in ServerMode.values()) {
        if (this == serverMode.urlPart) {
            return serverMode
        }
    }

    return null
}
