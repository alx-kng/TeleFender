package com.telefender.phone

import android.annotation.SuppressLint
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

/**
 * TODO: See if we can optimize memory / object creation to prevent too much work for
 *  garbage collector.
 *
 * TODO: When we add the notification channel here, maybe put a waiter on the notification
 *  channel creation, since we don't want the POST_NOTIFICATIONS request dialog to show before the
 *  default dialer request. If the default dialer is successfully granted first, I'm pretty sure
 *  we won't even need to request the POST_NOTIFICATIONS permission.
 */
class App : Application() {

    /**
     * TODO: Clean up scopes to allow for better structured concurrency. I think we might be creating
     *  too many local scopes in functions right now.
     *
     * TODO: Maybe consider injecting dispatchers through entire app.
     *
     * Scope uses Dispatchers.Default
     */
    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { ClientDatabase.getDatabase(this, applicationScope, contentResolver) }

    // TODO: See if this repository application context works
    val repository by lazy {
        ClientRepository(
            rawDao = database.rawDao(),

            executeAgentDao = database.executeAgentDao(),
            changeAgentDao = database.changeAgentDao(),
            uploadAgentDao = database.uploadAgentDao(),

            uploadChangeQueueDao = database.uploadChangeQueueDao(),
            uploadAnalyzedQueueDao = database.uploadAnalyzedQueueDao(),
            errorQueueDao = database.errorQueueDao(),

            executeQueueDao = database.executeQueueDao(),
            changeLogDao = database.changeLogDao(),
            storedMapDao = database.storedMapDao(),
            parametersDao = database.parametersDao(),

            callDetailDao = database.callDetailDao(),
            instanceDao = database.instanceDao(),
            contactDao = database.contactDao(),
            contactNumberDao = database.contactNumberDao(),
            analyzedNumberDao = database.analyzedNumberDao()
        )
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    //if we want, we could add custom crash reporting tree for release that would send error messages back to server
}
 