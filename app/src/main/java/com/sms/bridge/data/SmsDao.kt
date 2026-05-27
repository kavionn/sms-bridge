package com.sms.bridge.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_queue ORDER BY id ASC")
    fun getAllPendingSms(): Flow<List<SmsQueueItem>>

    @Query("SELECT * FROM sms_queue ORDER BY id ASC")
    suspend fun getPendingSmsList(): List<SmsQueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSms(sms: SmsQueueItem)

    @Delete
    suspend fun deleteSms(sms: SmsQueueItem)

    @Query("DELETE FROM sms_queue")
    suspend fun clearQueue()
}
