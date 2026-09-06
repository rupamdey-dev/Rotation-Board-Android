package com.rotationboard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DebugLogDao {
    @Query("SELECT * FROM debug_log ORDER BY id DESC LIMIT 300")
    suspend fun getRecent(): List<DebugLogEntity>

    @Insert
    suspend fun insert(entry: DebugLogEntity)

    @Query("DELETE FROM debug_log")
    suspend fun clear()
}
