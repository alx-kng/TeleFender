package com.telefender.phone.misc_helpers


enum class UserSetupStage(val serverString: String) {
    INITIAL("initial"),
    PERMISSIONS("permissions"),
    COMPLETE("complete")
}

/**
 * Converts serverStr to UserSetupStage if possible.
 */
fun String.toUserSetupStageFromServerStr() : UserSetupStage? {
    for (userSetupStage in UserSetupStage.values()) {
        if (this == userSetupStage.serverString) {
            return userSetupStage
        }
    }

    return null
}