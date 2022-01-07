package com.github.arekolek.phone

import android.app.Application
import com.github.arekolek.phone.database.ClientDatabase
import com.github.arekolek.phone.database.ClientRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

import timber.log.Timber

class App : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { ClientDatabase.getDatabase(this, applicationScope) }

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
        )
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
 