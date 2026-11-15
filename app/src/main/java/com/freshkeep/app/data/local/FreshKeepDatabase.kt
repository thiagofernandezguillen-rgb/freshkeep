package com.freshkeep.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun localDateNaarLong(datum: LocalDate?): Long? = datum?.toEpochDay()

    @TypeConverter
    fun longNaarLocalDate(waarde: Long?): LocalDate? = waarde?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantNaarLong(moment: Instant?): Long? = moment?.toEpochMilli()

    @TypeConverter
    fun longNaarInstant(waarde: Long?): Instant? = waarde?.let(Instant::ofEpochMilli)
}

@Database(
    entities = [Product::class, VoorraadItem::class, Geschiedenis::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FreshKeepDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun voorraadDao(): VoorraadDao
    abstract fun geschiedenisDao(): GeschiedenisDao
}
