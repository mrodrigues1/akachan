package com.babytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.babytracker.data.local.converter.Converters
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.SleepEntity

@Database(
    entities = [BreastfeedingEntity::class, SleepEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BabyTrackerDatabase : RoomDatabase() {
    abstract fun breastfeedingDao(): BreastfeedingDao
    abstract fun sleepDao(): SleepDao
}
