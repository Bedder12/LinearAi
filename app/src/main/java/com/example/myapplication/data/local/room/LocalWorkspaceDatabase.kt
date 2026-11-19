package com.example.myapplication.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        WorkspaceAnchorEntity::class,
        PageEntity::class,
        StrokeEntity::class,
        OutboxBatchEntity::class,
        OutboxEventEntity::class
    ],
    version = LocalWorkspaceDatabase.VERSION,
    exportSchema = true
)
internal abstract class LocalWorkspaceDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        const val VERSION = 1
        const val DATABASE_NAME = "linear-ai-workspace.db"

        fun open(context: Context): LocalWorkspaceDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LocalWorkspaceDatabase::class.java,
                DATABASE_NAME
            ).build()

        fun openForTest(context: Context, name: String): LocalWorkspaceDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LocalWorkspaceDatabase::class.java,
                name
            ).allowMainThreadQueries().build()
    }
}

object WorkspaceRepositoryProvider {
    @Volatile
    private var database: LocalWorkspaceDatabase? = null

    fun create(context: Context): com.example.myapplication.domain.persistence.WorkspaceRepository {
        val localDatabase = database ?: synchronized(this) {
            database ?: LocalWorkspaceDatabase.open(context).also { database = it }
        }
        return RoomWorkspaceRepository(localDatabase)
    }
}
