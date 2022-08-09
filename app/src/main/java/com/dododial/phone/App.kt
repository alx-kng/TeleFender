package com.dododial.phone

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.dododial.phone.data.dodo_database.ClientDatabase
import com.dododial.phone.data.dodo_database.ClientRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
//import com.dododial.phone.data.database.ClientDatabase
//import com.dododial.phone.data.database.ClientRepository
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
            database.queueToUploadDao(),
            database.instanceDao(),
            database.contactDao(),
            database.contactNumbersDao()
        )
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    //if we want, we could add custom crashreportingtree for release that would send error messages back to server
}
 