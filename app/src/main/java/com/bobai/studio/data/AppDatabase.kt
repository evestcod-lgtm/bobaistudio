package com.bobai.studio.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: Project)

    @Query("SELECT * FROM projects ORDER BY createdAtMs DESC")
    fun observeProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: String): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFragments(fragments: List<TimelineFragment>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFragment(fragment: TimelineFragment)

    @Query("SELECT * FROM fragments WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun observeFragments(projectId: String): Flow<List<TimelineFragment>>

    @Query("DELETE FROM fragments WHERE projectId = :projectId")
    suspend fun clearFragments(projectId: String)
}

@Database(entities = [Project::class, TimelineFragment::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bob_ai_studio.db"
                ).build().also { instance = it }
            }
    }
}
