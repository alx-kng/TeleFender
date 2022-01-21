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
    var setupState : WorkInfo.State? = null
    var oneTimeOmegaState : WorkInfo.State? = null
    var periodicOmegaState : WorkInfo.State? = null
    var downloadPostState : WorkInfo.State? = null
    var uploadPostState : WorkInfo.State? = null

    var oneTimeTokenState : WorkInfo.State? = null
    var periodicTokenState : WorkInfo.State? = null
    var uploadTokenState : WorkInfo.State? = null
}