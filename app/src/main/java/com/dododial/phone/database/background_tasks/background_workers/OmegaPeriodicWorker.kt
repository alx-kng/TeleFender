package com.dododial.phone.database.background_tasks.background_workers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.*
import com.dododial.phone.App
import com.dododial.phone.database.ClientDatabase
import com.dododial.phone.database.ClientRepository
import com.dododial.phone.database.background_tasks.TableSynchronizer
import com.dododial.phone.database.background_tasks.server_related.ServerHelpers
import java.util.*
import java.util.concurrent.TimeUnit

object OmegaPeriodicScheduler {

    val periodicOmegaWorkerTag = "periodicOmegaWorker"


    fun initiatePeriodicOmegaWorker(context : Context) : UUID {
        val uploadRequest = PeriodicWorkRequestBuilder<CoroutineOmegaPeriodicWorker>(15, TimeUnit.MINUTES)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS, // TODO Change backoff time to around a min
                TimeUnit.MILLISECONDS)
            .setInitialDelay(15, TimeUnit.SECONDS) // TODO Change delay to 30 min later
            .addTag(periodicOmegaWorkerTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(periodicOmegaWorkerTag, ExistingPeriodicWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }
}

class CoroutineOmegaPeriodicWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    val context: Context? = context

    @SuppressLint("LogNotTimber")
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {

        //SYNC
        val repository: ClientRepository? = (applicationContext as App).repository
        val database: ClientDatabase? = (applicationContext as App).database

        Log.i("DODODEBUG:", "OMEGA PERIODIC STARTED")
        if (context != null && database != null && repository != null) {

            /**
             * Sync with contacts and call logs
             */
            if (!repository.hasQTEs()) {
                TableSynchronizer.syncContacts(context, database, context.contentResolver)
                TableSynchronizer.syncCallLogs(database, context.contentResolver)
            } else {
                repository.executeAll()
                return Result.retry()
            }

            /**
             * Downloads changes from server
             */
            ServerHelpers.downloadPostRequest(context, repository, (applicationContext as App).applicationScope)

            /**
             * Executes logs in QueueToExecute
             */
            repository.executeAll()
            if (repository.hasQTEs()) {
                return Result.retry()
            }

            /**
             * Uploads changes to server
             */
            ServerHelpers.uploadPostRequest(context, repository, (applicationContext as App).applicationScope)
            if (repository.hasQTUs()) {
                return Result.retry()
            }

            Log.i("DODODEBUG:", "OMEGA PERIODIC ENDED")
            return Result.success()

        } else {
            return Result.retry()
        }

    }
}
