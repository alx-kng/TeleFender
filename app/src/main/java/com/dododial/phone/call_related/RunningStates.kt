package com.dododial.phone.call_related


/**
 * Used to keep track of what's currently active.
 */
object RunningStates {

    /**
     * Used so that a new intent to InCallActivity isn't created when the InCallActivity is already
     * active.
     */
    var callActivityRunning = false
}