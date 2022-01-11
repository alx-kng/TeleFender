package com.dododial.phone

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.dododial.phone.database.ClientDatabase
import com.dododial.phone.database.ClientRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
//import com.dododial.phone.database.ClientDatabase
//import com.dododial.phone.database.ClientRepository
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.SupervisorJob

import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
class App : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { ClientDatabase.getDatabase(this, applicationScope, contentResolver) }


    val repository by lazy {
        ClientRepository(
            database.callLogDao(),
            database.changeAgentDao(),
            database.uploadAgentDao(),
            database.changeLogDao(),
            database.executeAgentDao(),
            database.keyStorageDao(),
            database.queueToExecuteDao(),
            database.queueToUploadDao()   
        )
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
 