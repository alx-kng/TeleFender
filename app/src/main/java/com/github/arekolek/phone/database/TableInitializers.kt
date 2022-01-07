package com.github.arekolek.phone.database

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.PrimaryKey
import com.github.arekolek.phone.database.calllogs.CallDetails

object TableInitializers {

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initCallLog(context: Context, database: ClientDatabase) {

        var calls = CallDetails.getCallDetails(context)

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

    suspend fun initContactNumbers(context: Context, database: ClientDatabase) {

//        Given the contact, loop through all contact numbers
//
//        for (number in contact) {
//            changeFromClient( contactNumber insert args)
//        }

    }
}