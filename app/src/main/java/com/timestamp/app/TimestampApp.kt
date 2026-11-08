package com.timestamp.app

import android.app.Application
import com.timestamp.app.data.AppDatabase
import com.timestamp.app.data.ProjectRepository
import com.timestamp.app.data.SettingsRepository

class TimestampApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var repository: ProjectRepository
        private set
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.build(this)
        repository = ProjectRepository(database.projectDao())
        settings = SettingsRepository(this)
    }
}
