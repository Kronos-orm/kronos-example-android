package com.kotlinorm.example.android

import android.app.Application
import com.kotlinorm.Kronos
import com.kotlinorm.KronosLoggerApp
import com.kotlinorm.orm.ddl.table

class KronosExampleApp : Application() {
    lateinit var database: AndroidSQLiteDataSourceWrapper
        private set

    override fun onCreate() {
        super.onCreate()

        database = AndroidSQLiteDataSourceWrapper(this)
        KronosLoggerApp.detectLoggerImplementation()
        Kronos.dataSource = { database }

        database.table.syncTable(MarkdownDocument())
    }
}
