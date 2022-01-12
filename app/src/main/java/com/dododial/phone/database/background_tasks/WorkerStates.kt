package com.dododial.phone.database.background_tasks

import androidx.work.WorkInfo

internal object WorkerStates {

    var oneTimeExecState : WorkInfo.State? = null
    var periodicExecState : WorkInfo.State? = null
    var oneTimeUploadState : WorkInfo.State? = null
    var periodicUploadState : WorkInfo.State? = null
    var oneTimeDownloadState : WorkInfo.State? = null
    var periodicDownloadState : WorkInfo.State? = null
    var oneTimeSyncState : WorkInfo.State? = null
    var periodicSyncState : WorkInfo.State? = null

}