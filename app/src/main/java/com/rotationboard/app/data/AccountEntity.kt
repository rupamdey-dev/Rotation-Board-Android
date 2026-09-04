package com.rotationboard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val email: String,
    val project: String,
    val timerMode: String,      // "hours" or "time"
    val timerHours: Float,
    val timerTimeStr: String,   // "HH:MM" 24hr, empty when mode = hours
    val endTime: Long?          // epoch millis; null = never started / idle
)
