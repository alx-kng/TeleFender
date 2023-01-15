package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


/**
 * The data type specifies whether something is a (ChangeLog, AnalyzedNumber, or CallDetail).
 */
enum class GenericDataType(val serverString: String) {
    CHANGE_DATA("CHNG"), ANALYZED_DATA("ANUM"), LOG_DATA("CLOG")
}

/**
 * Converts serverStr to ChangeType if possible.
 */
fun String.toGenericDataType() : GenericDataType? {
    for (genericDataType in GenericDataType.values()) {
        if (this == genericDataType.serverString) {
            return genericDataType
        }
    }

    return null
}


/***************************************************************************************************
 * We only have one ExecuteQueue since it doesn't really matter right now if ChangeLogs,
 * AnalyzedNumbers, and CallDetails (although we probably won't download other user's CallDetails
 * due to data size) are executed in a mixed order. That is, we have no need to prioritize the
 * execution of a specific data type.
 **************************************************************************************************/

@Entity(tableName = "execute_queue")
data class ExecuteQueue(
    @PrimaryKey(autoGenerate = true)
    val rowID: Long = 0,
    val serverRowID: Long,
    val genericDataType: String,
    val linkedRowID: Long, // rowID within corresponding data table.
    val errorCounter : Int = 0
) {
    override fun toString() : String {
        return "EXECUTE LOG: rowID: $rowID executeType: $genericDataType linkedRowID: $linkedRowID errorCounter: $errorCounter"
    }

    companion object {
        /**
         * Creates ExecuteQueue log. Also allows you to modify arguments to fit form to database
         * (e.g., we can accept genericDataType as GenericDataType here and convert to string for
         * actual ExecuteQueue).
         */
        fun create(
            rowID: Long = 0,
            serverRowID: Long,
            genericDataType: GenericDataType,
            linkedRowID: Long,
            errorCounter: Int = 0
        ) : ExecuteQueue {

            return ExecuteQueue(
                rowID = rowID,
                serverRowID = serverRowID,
                genericDataType = genericDataType.serverString,
                linkedRowID = linkedRowID,
                errorCounter = errorCounter
            )
        }
    }
}

/***************************************************************************************************
 * We have 3 upload queues: UploadChangeQueue, UploadAnalyzedQueue, UploadLogQueue for easy
 * separation of concerns. That way, we can upload ChangeLogs, AnalyzedNumbers, and CallDetails
 * separately and have the server easily request them separately.
 **************************************************************************************************/

@Entity(tableName = "upload_change_queue",
    foreignKeys = [
        ForeignKey(
            entity = ChangeLog::class,
            parentColumns = arrayOf("rowID"),
            childColumns = arrayOf("linkedRowID"),
            onDelete = ForeignKey.NO_ACTION
        )],
)
data class UploadChangeQueue(
    @PrimaryKey val linkedRowID: Long,
    val errorCounter: Int = 0
) {
    override fun toString() : String {
        return "UPLOAD_CHANGE LOG: linkedRowID: $linkedRowID errorCounter: $errorCounter"
    }
}

@Entity(tableName = "upload_analyzed_queue",
    foreignKeys = [
        ForeignKey(
            entity = AnalyzedNumber::class,
            parentColumns = arrayOf("rowID"),
            childColumns = arrayOf("linkedRowID"),
            onDelete = ForeignKey.NO_ACTION
        )],
)
data class UploadAnalyzedQueue(
    @PrimaryKey val linkedRowID: Long,
    val errorCounter: Int = 0
) {
    override fun toString() : String {
        return "UPLOAD_ANALYZED LOG: linkedRowID: $linkedRowID errorCounter: $errorCounter"
    }
}

/***************************************************************************************************
 * This basically stores all error logs related to important actions such as execution and
 * downloading. We will be uploading these to the server as well and deleting the successfully
 * uploaded ones (as a queue should). We don't designate ErrorQueue as a typical "Upload" queue
 * because it contains some new important info, whereas the upload queues simply link to the data.
 **************************************************************************************************/

@JsonClass(generateAdapter = true)
@Entity(tableName = "error_queue",
    indices = [
        Index(value = ["serverRowID"], unique = true),
    ]
)
data class ErrorQueue(
    @PrimaryKey(autoGenerate = true)
    val rowID: Long = 0,
    val instanceNumber: String,
    val serverRowID: Long,
    val errorJson: String,
    val errorCounter : Int = 0
) {

    override fun toString(): String {
        return "ERROR LOG: rowID: $rowID instanceNumber: $instanceNumber serverRowID: $serverRowID errorJson$errorJson"
    }

    companion object {
        /**
         * Creates ErrorQueue log. Also allows you to modify arguments to fit form to database
         * (e.g., we can accept genericDataType as GenericDataType here and convert to string for
         * actual ErrorQueue).
         */
        fun create(
            instanceNumber: String,
            serverRowID: Long,
            errorType: ErrorType,
            errorMessage: String,
            errorDataType: GenericDataType,
            errorDataJson: String
        ) : ErrorQueue {

            val error = Error.create(
                errorType = errorType,
                errorMessage = errorMessage,
                errorDataType = errorDataType,
                errorDataJson = errorDataJson
            )

            return ErrorQueue(
                instanceNumber = instanceNumber,
                serverRowID = serverRowID,
                errorJson = error.toJson()
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class Error(
    val errorType: String, // Execute error
    val errorMessage: String,
    val errorDataType: String, // ANALYZED_NUMBER, CALL_LOG
    val errorDataJson: String, // ChangeLog json
) {

    fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(Error::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString(): String {
        return "errorType: $errorType errorMessage: $errorMessage errorDataType: $errorDataType" +
            "errorDataJson: $errorDataJson"
    }

    companion object {
        /**
         * Creates ErrorQueue log. Also allows you to modify arguments to fit form to database
         * (e.g., we can accept genericDataType as GenericDataType here and convert to string for
         * actual ErrorQueue).
         */
        fun create(
            errorType: ErrorType,
            errorMessage: String,
            errorDataType: GenericDataType,
            errorDataJson: String
        ) : Error {

            return Error(
                errorType = errorType.serverString,
                errorMessage = errorMessage,
                errorDataType = errorDataType.serverString,
                errorDataJson = errorDataJson
            )
        }
    }
}

/**
 * The error type specifies the general cause of error. More specific problems should go in the
 * actual error message.
 */
enum class ErrorType(val serverString: String) {
    DOWNLOAD_ERROR("DNERR"), EXECUTE_ERROR("EXERR")
}
