package com.kotlinorm.example.android

import android.app.Application
import com.kotlinorm.Kronos
import com.kotlinorm.KronosLoggerApp
import com.kotlinorm.orm.ddl.table
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class KronosExampleApp : Application() {
    lateinit var database: AndroidSQLiteDataSourceWrapper
        private set
    private val schemaExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var schemaReady: Future<*>

    override fun onCreate() {
        super.onCreate()

        database = AndroidSQLiteDataSourceWrapper(this)
        KronosLoggerApp.detectLoggerImplementation()
        Kronos.dataSource = { database }

        schemaReady = schemaExecutor.submit {
            database.table.syncTable(MarkdownDocument())
        }
    }

    fun awaitSchemaReady() {
        schemaReady.get()
    }

    override fun onTerminate() {
        schemaExecutor.shutdownNow()
        super.onTerminate()
    }
}
