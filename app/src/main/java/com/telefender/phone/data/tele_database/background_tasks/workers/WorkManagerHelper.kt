package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber

object WorkManagerHelper {

    /**
     * Retrieves state of unique work.
     */
    fun getUniqueWorkerState(context: Context, tag: String) : WorkInfo.State? {
        val instance = WorkManager.getInstance(context)
        val statuses: ListenableFuture<List<WorkInfo>> = instance.getWorkInfosForUniqueWork(tag)

        try {
            return statuses.get().firstOrNull()?.state
        } catch (e: Exception) {
            Timber.e("Exception in getUniqueWorkState()")
        }

        return null
    }
}