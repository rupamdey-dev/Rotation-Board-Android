package com.rotationboard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debug_log")
data class DebugLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val message: String
)
