package com.rotationboard.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY id DESC")
    fun observeForUser(userId: Long): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE endTime IS NOT NULL")
    suspend fun getAllScheduled(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE endTime IS NOT NULL AND endTime <= :now AND rung = 0")
    suspend fun getOverdueUnrung(now: Long): List<AccountEntity>

    @Query("UPDATE accounts SET rung = 1 WHERE id = :id")
    suspend fun markRung(id: Long)

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}
