package com.telefender.phone.data.tele_database

import android.drm.DrmStore.Action.EXECUTE
import kotlinx.coroutines.sync.Mutex


enum class MutexType {
    UPLOAD_CHANGE, UPLOAD_ANALYZED, UPLOAD_LOG, ERROR_LOG, EXECUTE, CHANGE, STORED_MAP, PARAMETERS,
    INSTANCE, CONTACT, CONTACT_NUMBER, CALL_DETAIL, ANALYZED, SYNC, DEBUG_DATA,
}

/**
 * Mutex locks to prevent conflicting access to same table, which makes our database thread safe
 * (even if Room is already supposedly thread safe). Note that we only locks on writing queries,
 * that is, insert / update / delete queries.
 */
object TeleLocks {
    private val mutexUploadChange = Mutex()
    private val mutexUploadAnalyzed = Mutex()
    private val mutexUploadLog = Mutex()
    private val mutexErrorLog = Mutex()

    private val mutexExecute = Mutex()
    private val mutexChange = Mutex()
    private val mutexStoredMap = Mutex()
    private val mutexParameters = Mutex()

    private val mutexCallDetails = Mutex()
    private val mutexInstance = Mutex()
    private val mutexContact = Mutex()
    private val mutexContactNumber = Mutex()
    private val mutexAnalyzed = Mutex()

    private val mutexSync = Mutex()

    private val mutexDebugData = Mutex()

    // Storing inside map prevents name conflicts.
    val mutexLocks = mapOf(
        MutexType.UPLOAD_CHANGE to mutexUploadChange,
        MutexType.UPLOAD_ANALYZED to mutexUploadAnalyzed,
        MutexType.UPLOAD_LOG to mutexUploadLog,
        MutexType.ERROR_LOG to mutexErrorLog,

        MutexType.EXECUTE to mutexExecute,
        MutexType.CHANGE to mutexChange,
        MutexType.STORED_MAP to mutexStoredMap,
        MutexType.PARAMETERS to mutexParameters,

        MutexType.CALL_DETAIL to mutexCallDetails,
        MutexType.INSTANCE to mutexInstance,
        MutexType.CONTACT to mutexContact,
        MutexType.CONTACT_NUMBER to mutexContactNumber,
        MutexType.ANALYZED to mutexAnalyzed,

        MutexType.SYNC to mutexSync,

        MutexType.DEBUG_DATA to mutexDebugData
    )
}