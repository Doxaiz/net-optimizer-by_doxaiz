package com.doxaiz.netoptimizer

import android.app.Application
import androidx.room.Room
import com.doxaiz.netoptimizer.core.data.DoxaizDatabase
import com.doxaiz.netoptimizer.core.data.SessionRepository

class DoxaizApp : Application() {
    lateinit var database: DoxaizDatabase
        private set
    lateinit var sessionRepository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, DoxaizDatabase::class.java, "doxaiz.db").build()
        sessionRepository = SessionRepository(database.sessionDao())
    }
}
