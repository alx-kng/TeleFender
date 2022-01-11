package com.dododial.phone.database.background_tasks

import androidx.work.WorkInfo

internal object WorkerStates {
    var initExecState: WorkInfo.State? = WorkInfo.State.RUNNING

}