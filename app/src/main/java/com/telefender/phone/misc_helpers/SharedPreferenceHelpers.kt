package com.telefender.phone.misc_helpers

import android.content.Context
import androidx.preference.PreferenceManager
import com.telefender.phone.data.server_related.ServerMode
import com.telefender.phone.data.server_related.toServerModeFromUrlPart


enum class SharedPreferenceKey(val keyString: String) {
    USER_SETUP_STAGE("userSetupStage"),
    SERVER_MODE_URL("serverModeUrl"),
    SESSION_ID("sessionID"),
    INSTANCE_NUMBER("instanceNumber"),
    CLIENT_KEY("clientKey")
}

object SharedPreferenceHelpers {

    /**
     * Sets user setup stage SharedPreference (e.g., INITIAL, PERMISSIONS, etc.).
     */
    fun setUserSetupStage(
        context: Context,
        userSetupStage: UserSetupStage
    ) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()

        editor.putString(SharedPreferenceKey.USER_SETUP_STAGE.keyString, userSetupStage.serverString)
        editor.apply()
    }


    /**
     * Sets session ID SharedPreference.
     */
    fun setSessionID(
        context: Context,
        sessionID: String
    ) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()

        editor.putString(SharedPreferenceKey.SESSION_ID.keyString, sessionID)
        editor.apply()
    }

    /**
     * Sets modified URL based off current server mode (e.g., dev, stage, production, etc.).
     */
    fun setServerMode(
        context: Context,
        serverMode: ServerMode
    ) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()

        editor.putString(SharedPreferenceKey.SERVER_MODE_URL.keyString, serverMode.urlPart)
        editor.apply()
    }

    /**
     * Sets instance number SharedPreference.
     */
    fun setInstanceNumber(
        context: Context,
        instanceNumber: String
    ) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()

        editor.putString(SharedPreferenceKey.INSTANCE_NUMBER.keyString, instanceNumber)
        editor.apply()
    }

    /**
     * Sets client key SharedPreference.
     */
    fun setClientKey(
        context: Context,
        clientKey: String
    ) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPreferences.edit()

        editor.putString(SharedPreferenceKey.CLIENT_KEY.keyString, clientKey)
        editor.apply()
    }

    /**
     * Gets user setup stage SharedPreference (e.g., INITIAL, PERMISSIONS, etc.).
     */
    fun getUserSetupStage(context: Context) : UserSetupStage {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val serverString = sharedPreferences.getString(
            SharedPreferenceKey.USER_SETUP_STAGE.keyString,
            UserSetupStage.INITIAL.serverString
        )
        return serverString?.toUserSetupStageFromServerStr() ?: UserSetupStage.INITIAL
    }

    /**
     * Gets session ID SharedPreference.
     */
    fun getSessionID(context: Context) : String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(SharedPreferenceKey.SESSION_ID.keyString, null)
    }


    /**
     * Gets modified URL based off current server mode (e.g., dev, stage, production, etc.).
     */
    fun getServerModeUrl(
        context: Context,
        baseURL: String
    ) : String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val urlPart = sharedPreferences.getString(
            SharedPreferenceKey.SERVER_MODE_URL.keyString,
            ServerMode.TEST.urlPart
        )
        return "https://$urlPart$baseURL"
    }


    fun getServerMode(context: Context) : ServerMode {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val urlPart = sharedPreferences.getString(
            SharedPreferenceKey.SERVER_MODE_URL.keyString,
            ServerMode.TEST.urlPart
        )
        return urlPart?.toServerModeFromUrlPart() ?: ServerMode.TEST
    }

    /**
     * Gets instance number SharedPreference.
     */
    fun getInstanceNumber(context: Context) : String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(SharedPreferenceKey.INSTANCE_NUMBER.keyString, null)
    }

    /**
     * Gets client key SharedPreference.
     */
    fun getClientKey(context: Context) : String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(SharedPreferenceKey.CLIENT_KEY.keyString, null)
    }
}




