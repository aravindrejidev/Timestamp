package com.timestamp.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val audioUri: String,
    val lyricsJson: String,
    val durationMs: Long,
    val updatedAt: Long,
    val totalLines: Int = 0,
    val syncedLines: Int = 0,
    val ttmlFontSize: Int = 24,
    val ttmlColor: String = "#FFFFFF",
    val ttmlPosition: String = "bottom"
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "timestamp.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
