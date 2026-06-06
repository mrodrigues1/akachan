package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babytracker.data.local.entity.BabyProfileEntity
import com.babytracker.data.local.entity.SINGLETON_ID

@Dao
interface BabyProfileDao {
    @Query("SELECT * FROM babies WHERE id = $SINGLETON_ID LIMIT 1")
    suspend fun getProfile(): BabyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: BabyProfileEntity)
}
