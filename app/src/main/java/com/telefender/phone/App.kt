package com.telefender.phone

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
//import com.dododial.phone.data.database.ClientDatabase
//import com.dododial.phone.data.database.ClientRepository
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.SupervisorJob

import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
class App : Application() {

    // TODO: Maybe consider injecting dispatchers through entire app.
    // Scope uses Dispatchers.Default
    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { ClientDatabase.getDatabase(this, applicationScope, contentResolver) }

    // TODO: See if this repository application context works
    val repository by lazy {
        ClientRepository(
            database.callDetailDao(),
            database.changeAgentDao(),
            database.uploadAgentDao(),
            database.changeLogDao(),
            database.executeAgentDao(),
            database.storedMapDao(),
            database.queueToExecuteDao(),
            database.queueToUploadDao(),
            database.instanceDao(),
            database.contactDao(),
            database.contactNumbersDao(),
            database.safeLogDao()
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
 