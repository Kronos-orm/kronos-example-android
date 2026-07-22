@file:OptIn(com.kotlinorm.annotations.InternalKronosApi::class)

package com.kotlinorm.example.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kotlinorm.Kronos
import com.kotlinorm.adapter.AndroidUtilLoggerAdapter
import com.kotlinorm.beans.logging.KLogMessage
import com.kotlinorm.beans.task.KronosAtomicActionTask
import com.kotlinorm.beans.task.KronosAtomicQueryTask
import com.kotlinorm.enums.KLoggerType
import com.kotlinorm.enums.KOperationType
import com.kotlinorm.orm.ddl.table
import com.kotlinorm.utils.GeneratedTypeProvider
import java.math.BigDecimal
import java.math.BigInteger
import java.util.ServiceLoader
import kotlin.reflect.typeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKronosIntegrationTest {
    @Test
    fun rawNumberBindingsPreserveDecimalAndLargeIntegerText() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as KronosExampleApp
        application.awaitSchemaReady()

        val decimal = BigDecimal("19.99")
        val largeInteger = BigInteger("123456789012345678901234567890")
        val decimalTitle = "decimal-${System.nanoTime()}"
        val integerTitle = "integer-${System.nanoTime()}"

        assertEquals(1, application.database.update(rawContentInsert(decimalTitle, decimal)))
        assertEquals(1, application.database.update(rawContentInsert(integerTitle, largeInteger)))

        assertEquals(decimal.toPlainString(), application.database.first(rawContentQuery(decimalTitle)))
        assertEquals(largeInteger.toString(), application.database.first(rawContentQuery(integerTitle)))
    }

    @Test
    fun generatedProviderSupportsAndroidCrudWithoutManualRegistration() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as KronosExampleApp
        application.awaitSchemaReady()
        val providers = ServiceLoader.load(GeneratedTypeProvider::class.java).toList()

        assertEquals(KLoggerType.ANDROID_LOGGER, Kronos.loggerType)
        val logger = Kronos.defaultLogger("KronosAndroidTest")
        assertTrue(logger is AndroidUtilLoggerAdapter)
        logger.info(arrayOf(KLogMessage("Kronos Android logger instrumentation probe")))
        assertTrue(providers.any { it.id.startsWith("gradle:") })
        assertEquals(
            MarkdownDocument::class,
            Kronos.createKPojo(typeOf<MarkdownDocument>())::class
        )

        val repository = DocumentRepository()
        val created = repository.create("provider-${System.nanoTime()}", "generated factory", false)
        assertNotNull(created.id)
        assertEquals(created.id, repository.find(requireNotNull(created.id))?.id)

        val rollbackTitle = "rollback-${System.nanoTime()}"
        runCatching {
            Kronos.transact {
                repository.create(rollbackTitle, "rollback", false)
                error("force rollback")
            }
        }
        assertNull(repository.all().firstOrNull { it.title == rollbackTitle })

        application.database.table.dropTable(MarkdownDocument())
        application.database.table.syncTable(MarkdownDocument())
    }

    private fun rawContentInsert(title: String, content: Number): KronosAtomicActionTask =
        KronosAtomicActionTask(
            sql = "INSERT INTO markdown_documents (title, content) VALUES (:title, :content)",
            paramMap = mapOf("title" to title, "content" to content),
            operationType = KOperationType.INSERT
        )

    private fun rawContentQuery(title: String): KronosAtomicQueryTask =
        KronosAtomicQueryTask(
            sql = "SELECT content FROM markdown_documents WHERE title = :title",
            paramMap = mapOf("title" to title),
            targetType = typeOf<String>()
        )
}
