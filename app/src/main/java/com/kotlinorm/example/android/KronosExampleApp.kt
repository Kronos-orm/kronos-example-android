package com.kotlinorm.example.android

import android.app.Application
import com.kotlinorm.Kronos
import com.kotlinorm.orm.ddl.table
import com.kotlinorm.utils.registerKPojo

class KronosExampleApp : Application() {
    lateinit var database: AndroidSQLiteDataSourceWrapper
        private set

    override fun onCreate() {
        super.onCreate()

        // Android APK merging does not reliably preserve generated ServiceLoader entries,
        // so this sample registers its KPojo explicitly.
        registerKPojo(MarkdownDocument::class) { MarkdownDocument() }

        database = AndroidSQLiteDataSourceWrapper(this)
        Kronos.logPath = emptyList()
        Kronos.dataSource = { database }

        database.table.syncTable(MarkdownDocument())
    }
}
