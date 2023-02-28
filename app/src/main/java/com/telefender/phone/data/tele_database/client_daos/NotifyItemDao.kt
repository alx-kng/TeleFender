package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.NotifyItem

@Dao
interface NotifyItemDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotifyItem(notifyItem: NotifyItem) : Long

    /**
     * Retrieves all NotifyItems. Annotated with Transaction for large query safety.
     */
    @Transaction
    @Query("SELECT * FROM notify_item")
    suspend fun getAllNotifyItem() : List<NotifyItem>

    /**
     * Retrieves NotifyItem if it exists, given the rowID.
     */
    @Query("SELECT * FROM notify_item WHERE rowID = :rowID")
    suspend fun getNotifyItem(rowID: Long) : NotifyItem?

    /**
     * Retrieves NotifyItem if it exists, given the normalizedNumber.
     */
    @Query("SELECT * FROM notify_item WHERE normalizedNumber = :normalizedNumber")
    suspend fun getNotifyItem(normalizedNumber: String) : NotifyItem?

    /**
     * Returns whether or not the update was successful.
     */
    suspend fun updateNotifyItem(notifyItem: NotifyItem) : Boolean {
        return updateNotifyItemQuery(notifyItem) == 1
    }

    /**
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed (reflects # updated rows).
     */
    @Update
    suspend fun updateNotifyItemQuery(notifyItem: NotifyItem) : Int?

    /**
     * Returns whether or not the update was successful.
     */
    suspend fun updateNotifyItem(
        normalizedNumber: String,
        lastCallTime: Long? = null,
        lastQualifiedTime: Long? = null,
        veryFirstSeenTime: Long? = null,
        seenSinceLastCall: Boolean? = null,
        notifyWindow: List<Long>? = null,
        currDropWindow: Int? = null,
        nextDropWindow: Int? = null
    ) : Boolean {

        // Should only update if the row already exists.
        if (getNotifyItem(normalizedNumber) == null) return false

        val result = updateNotifyItemQuery(
            normalizedNumber = normalizedNumber,
            lastCallTime = lastCallTime,
            lastQualifiedTime = lastQualifiedTime,
            veryFirstSeenTime = veryFirstSeenTime,
            seenSinceLastCall = seenSinceLastCall,
            notifyWindow = notifyWindow,
            currDropWindow = currDropWindow,
            nextDropWindow = nextDropWindow
        )

        return result == 1
    }

    /**
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed (reflects # updated rows).
     */
    @Query(
        """UPDATE notify_item SET
        lastCallTime =
            CASE
                WHEN :lastCallTime IS NOT NULL
                    THEN :lastCallTime
                ELSE lastCallTime
            END,
        lastQualifiedTime =
            CASE
                WHEN :lastQualifiedTime IS NOT NULL
                    THEN :lastQualifiedTime
                ELSE lastQualifiedTime
            END,
        veryFirstSeenTime =
        CASE
            WHEN :veryFirstSeenTime IS NOT NULL
                THEN :veryFirstSeenTime
            ELSE veryFirstSeenTime
        END,
        seenSinceLastCall =
            CASE
                WHEN :seenSinceLastCall IS NOT NULL
                    THEN :seenSinceLastCall
                ELSE seenSinceLastCall
            END,
        notifyWindow =
            CASE
                WHEN :notifyWindow IS NOT NULL
                    THEN :notifyWindow
                ELSE notifyWindow
            END,
        currDropWindow =
            CASE
                WHEN :currDropWindow IS NOT NULL
                    THEN :currDropWindow
                ELSE currDropWindow
            END,
        nextDropWindow =
            CASE
                WHEN :nextDropWindow IS NOT NULL
                    THEN :nextDropWindow
                ELSE nextDropWindow
            END
        WHERE normalizedNumber = :normalizedNumber"""
    )
    suspend fun updateNotifyItemQuery(
        normalizedNumber: String,
        lastCallTime: Long?,
        lastQualifiedTime: Long?,
        veryFirstSeenTime: Long?,
        seenSinceLastCall: Boolean?,
        notifyWindow: List<Long>?,
        currDropWindow: Int?,
        nextDropWindow: Int?
    ) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed (reflects # deleted rows).
     */
    @Query("DELETE FROM notify_item WHERE rowID = :rowID")
    suspend fun deleteNotifyItem(rowID: Long) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed (reflects # deleted rows).
     */
    @Query("DELETE FROM notify_item WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteNotifyItem(normalizedNumber: String) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful (number of rows
     * delete). If a value >0 is returned, then the delete was at least partially successful,
     * otherwise the delete completely failed (if there were existing rows).
     */
    @Query("DELETE FROM notify_item")
    suspend fun deleteAllNotifyItem() : Int?
}