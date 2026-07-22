@file:OptIn(com.kotlinorm.annotations.InternalKronosApi::class)

package com.kotlinorm.example.android

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteProgram
import com.kotlinorm.Kronos
import com.kotlinorm.beans.task.KronosAtomicBatchTask
import com.kotlinorm.beans.task.ResultColumnMetadata
import com.kotlinorm.beans.task.TransactionScope
import com.kotlinorm.enums.DBType
import com.kotlinorm.enums.KOperationType
import com.kotlinorm.enums.TransactionIsolation
import com.kotlinorm.interfaces.KAtomicActionTask
import com.kotlinorm.interfaces.KAtomicQueryTask
import com.kotlinorm.interfaces.KPojo
import com.kotlinorm.interfaces.KronosDataSourceWrapper
import com.kotlinorm.utils.decodeDatabaseValue
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

/**
 * Small Android SQLite adapter for the example app.
 *
 * Kronos renders SQLite SQL and named parameters. Android's SQLite API has no JDBC
 * layer, so this class translates the parsed task into SQLiteProgram bindings and
 * maps cursor rows through the generated KPojo mapper.
 */
class AndroidSQLiteDataSourceWrapper(context: Context) : KronosDataSourceWrapper {
    private val helper = object : SQLiteOpenHelper(context, "kronos-example.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) = Unit

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private val transactionDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

    override val url: String = "sqlite://kronos-example.db"
    override val userName: String = "android"
    override val dbType: DBType = DBType.SQLite

    private val database: SQLiteDatabase
        get() = helper.writableDatabase

    override fun toList(task: KAtomicQueryTask): List<Any?> {
        val parsed = task.parsed()
        val rows = mutableListOf<Any?>()
        query(parsed.jdbcSql, parsed.jdbcParamList) { cursor ->
            while (cursor.moveToNext()) {
                rows += readRow(cursor, task)
            }
        }
        return rows
    }

    override fun first(task: KAtomicQueryTask): Any? = toList(task).firstOrNull()

    override fun update(task: KAtomicActionTask): Int {
        val parsed = task.parsed()
        return database.compileStatement(parsed.jdbcSql).use { statement ->
            statement.bindValues(parsed.jdbcParamList)
            when (task.operationType) {
                KOperationType.INSERT -> {
                    val generatedId = statement.executeInsert()
                    if (task.generatedKeyRequest != null && generatedId >= 0) {
                        task.lastInsertId = generatedId
                    }
                    if (generatedId >= 0) 1 else 0
                }

                KOperationType.UPDATE,
                KOperationType.DELETE,
                KOperationType.UPSERT -> statement.executeUpdateDelete()

                else -> {
                    statement.execute()
                    0
                }
            }
        }
    }

    override fun batchUpdate(task: KronosAtomicBatchTask): IntArray =
        task.parsedSqlArr().map { parsed ->
            database.compileStatement(parsed.jdbcSql).use { statement ->
                statement.bindValues(parsed.jdbcParamList)
                when (task.operationType) {
                    KOperationType.INSERT -> if (statement.executeInsert() >= 0) 1 else 0
                    KOperationType.UPDATE,
                    KOperationType.DELETE,
                    KOperationType.UPSERT -> statement.executeUpdateDelete()

                    else -> {
                        statement.execute()
                        0
                    }
                }
            }
        }.toIntArray()

    override fun transact(
        isolation: TransactionIsolation?,
        timeout: Int?,
        block: TransactionScope.() -> Any?
    ): Any? {
        require(isolation == null) { "Android SQLite does not expose JDBC isolation levels" }
        require(timeout == null) { "Android SQLite does not expose JDBC transaction timeouts" }

        if ((transactionDepth.get() ?: 0) > 0) {
            return TransactionScope().block()
        }

        transactionDepth.set(1)
        database.beginTransaction()
        return try {
            val result = TransactionScope().block()
            database.setTransactionSuccessful()
            result
        } finally {
            database.endTransaction()
            transactionDepth.remove()
        }
    }

    private fun query(sql: String, parameters: Array<Any?>, block: (Cursor) -> Unit) {
        val cursor = database.rawQueryWithFactory(
            { _, driver, editTable, query ->
                query.bindValues(parameters)
                SQLiteCursor(driver, editTable, query)
            },
            sql,
            emptyArray(),
            ""
        )
        cursor.use(block)
    }

    private fun readRow(cursor: Cursor, task: KAtomicQueryTask): Any? {
        val targetType = task.targetType
        val classifier = targetType.classifier as? KClass<*> ?: return null
        return when {
            classifier == Map::class || classifier == MutableMap::class -> {
                val valueType = targetType.arguments.getOrNull(1)?.type
                    ?.takeUnless { (it.classifier as? KClass<*>) == Any::class }
                linkedMapOf<String, Any?>().apply {
                    for (position in 0 until cursor.columnCount) {
                        val label = cursor.getColumnName(position)
                        val metadata = task.resultColumns.columnMetadata(label)
                        val column = when {
                            valueType != null && metadata != null ->
                                metadata.copy(type = valueType, columnLabel = label)
                            valueType != null -> ResultColumnMetadata(valueType, columnLabel = label)
                            metadata != null -> metadata.withLabel(label)
                            else -> null
                        }
                        val value = cursor.valueAt(position)
                        this[label] = column?.decode(value) ?: value
                    }
                }
            }

            KPojo::class.java.isAssignableFrom(classifier.java) -> {
                val prototype = Kronos.createKPojo(targetType)
                prototype.apply {
                    for (position in 0 until cursor.columnCount) {
                        val label = cursor.getColumnName(position)
                        val field = __columns.firstOrNull {
                            it.name.equals(label, ignoreCase = true) ||
                                it.columnName.equals(label, ignoreCase = true)
                        } ?: continue
                        val fieldType = field.kType ?: typeOf<Any?>()
                        val column = task.resultColumns.columnMetadata(label)
                            ?.copy(type = fieldType, columnLabel = label)
                            ?: ResultColumnMetadata(fieldType, field, columnLabel = label)
                        this[field.name] = column.decode(cursor.valueAt(position))
                    }
                }
            }

            else -> {
                val label = cursor.getColumnName(0)
                val metadata = task.resultColumns.columnMetadata(label) ?: task.resultColumns.values.singleOrNull()
                val logicalType = if ((targetType.classifier as? KClass<*>) == Any::class && metadata != null) {
                    metadata.type
                } else {
                    targetType
                }
                val column = metadata?.copy(type = logicalType, columnLabel = label)
                    ?: ResultColumnMetadata(logicalType, columnLabel = label)
                column.decode(cursor.valueAt(0))
            }
        }
    }

    private fun ResultColumnMetadata.decode(value: Any?): Any? =
        decodeDatabaseValue(value, this, sqlDialect, columnLabel)

    private fun Map<String, ResultColumnMetadata>.columnMetadata(label: String): ResultColumnMetadata? {
        this[label]?.let { return it }
        val matches = entries.filter { it.key.equals(label, ignoreCase = true) }
        return when (matches.size) {
            0 -> null
            1 -> matches.single().value
            else -> error("Ambiguous result column metadata for label '$label'")
        }
    }

    private fun ResultColumnMetadata.withLabel(label: String): ResultColumnMetadata =
        if (columnLabel == label) this else copy(columnLabel = label)

    private fun Cursor.valueAt(position: Int): Any? = when (getType(position)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(position)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(position)
        Cursor.FIELD_TYPE_BLOB -> getBlob(position)
        else -> getString(position)
    }

    private fun SQLiteProgram.bindValues(values: Array<Any?>) {
        values.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> bindNull(position)
                is ByteArray -> bindBlob(position, value)
                is Byte, is Short, is Int, is Long -> bindLong(position, value.toLong())
                is Float, is Double -> bindDouble(position, value.toDouble())
                is BigDecimal -> bindString(position, value.toPlainString())
                is BigInteger -> bindString(position, value.toString())
                is Number -> bindString(position, value.toString())
                is Boolean -> bindLong(position, if (value) 1L else 0L)
                else -> bindString(position, value.toString())
            }
        }
    }
}
