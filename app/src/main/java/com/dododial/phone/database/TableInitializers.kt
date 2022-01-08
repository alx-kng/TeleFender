package com.dododial.phone.database

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.dododial.phone.database.android_db.CallDetailHelper
import com.dododial.phone.database.android_db.ContactDetailsHelper

object TableInitializers {

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initCallLog(context: Context, database: ClientDatabase) {

        var calls = CallDetailHelper.getCallDetails(context)

        for (call in calls) {
            var log = CallLog(
                call.number,
                call.callType,
                call.callEpochDate,
                call.callDuration,
                call.callLocation,
                call.callDirection)

            database.callLogDao().insertLog(log)
        }
    }

    //TODO finish table initializers
// Each of these go through changeAgentDao and allow it to pass updates to changelog/execlog/uploadlog
    suspend fun initInstance(context: Context, database: ClientDatabase) {

        val changeID = "Placeholder UUID" // create a new UUID
        val instanceNumber = "Placeholder instanceNumber" // get phone # of user
        val changeTime = "Placeholder changetime" // get epoch time
        val type = "insInsert"

        database.changeAgentDao().changeFromClient(
            changeID,
            instanceNumber,
            changeTime,
            type, null, null, null, null, null, null, null
        )
    }

    suspend fun initContact(context: Context, database: ClientDatabase) {

//        For each contact and contact number on device, create ChangeLog for cInsert
//        Pseudo code:

//        for (contact in Contacts) {
//            changeFromClient( with contact)
//            initContactNumbers(context, database, contact)
//        }


    }

    // will take another argument for contact

    suspend fun initContactNumbers(context: Context, database: ClientDatabase) {}

//        Given the contact, loop through all contact numbers
//
//        for (number in contact) {
//            changeFromClient( contactNumber insert args)
//        }
    
   

}