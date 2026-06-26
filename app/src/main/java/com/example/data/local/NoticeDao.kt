package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Notice
import kotlinx.coroutines.flow.Flow

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notices ORDER BY scannedAt DESC")
    fun getAllNotices(): Flow<List<Notice>>

    @Query("SELECT * FROM notices WHERE id = :id LIMIT 1")
    suspend fun getNoticeById(id: String): Notice?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotices(notices: List<Notice>): List<Long>

    @Query("UPDATE notices SET isNew = 0 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notices SET isNew = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notices WHERE id = :id")
    suspend fun deleteNotice(id: String)

    @Query("DELETE FROM notices")
    suspend fun clearAll()
}
