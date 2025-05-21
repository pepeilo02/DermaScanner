package com.example.dermascanner

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity
data class PhotoEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imagePath: String,
    val croppedImagePath: String,
    val maskPath: String,
    val prediction: String,
    val confidence: Float,
    val timestamp: Long
)

@Dao
interface PhotoEntryDao{
    @Query("SELECT * FROM PhotoEntry ORDER BY timestamp ASC")
    fun getAll(): List<PhotoEntry>

    @Query("SELECT * FROM PhotoEntry WHERE id = :id")
    fun getById(id: Int): PhotoEntry?

    @Delete
    fun delete(photoEntry: PhotoEntry)

    @Insert
    fun insert(photoEntry: PhotoEntry)
}

@Database(entities = [PhotoEntry::class], version= 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoEntryDao(): PhotoEntryDao
}

